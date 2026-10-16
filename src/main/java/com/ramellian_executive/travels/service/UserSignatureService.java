package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.model.SavedSignatureResponse;
import com.ramellian_executive.travels.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;

@Service
public class UserSignatureService {

    private final UserRepository userRepository;
    private final Path signatureDirectory;

    public UserSignatureService(UserRepository userRepository,
                                @Value("${app.signature.directory:uploads/signatures}") String dir) {
        this.userRepository = userRepository;
        this.signatureDirectory = Paths.get(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public SavedSignatureResponse save(MultipartFile file, boolean consent, Authentication authentication) {
        if (!consent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must confirm that this is your signature before saving it");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature image is required");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature must be an image file");
        }

        User user = findUser(authentication);
        String ext = resolveExtension(file.getContentType());
        String fileName = user.getId() + "-profile-signature." + ext;

        try {
            Files.createDirectories(signatureDirectory);
            Files.copy(file.getInputStream(), signatureDirectory.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not store signature", ex);
        }

        // Delete old signature file if it changed
        if (user.getSignatureFileName() != null && !user.getSignatureFileName().equals(fileName)) {
            try { Files.deleteIfExists(signatureDirectory.resolve(user.getSignatureFileName())); } catch (IOException ignored) {}
        }

        user.setSignatureFileName(fileName);
        user.setSignatureConsentGivenAt(ZonedDateTime.now());
        userRepository.save(user);

        return new SavedSignatureResponse(true, user.getSignatureConsentGivenAt());
    }

    @Transactional
    public void remove(Authentication authentication) {
        User user = findUser(authentication);
        if (user.getSignatureFileName() != null) {
            try { Files.deleteIfExists(signatureDirectory.resolve(user.getSignatureFileName())); } catch (IOException ignored) {}
            user.setSignatureFileName(null);
            user.setSignatureConsentGivenAt(null);
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public SavedSignatureResponse status(Authentication authentication) {
        User user = findUser(authentication);
        return new SavedSignatureResponse(
                user.getSignatureFileName() != null,
                user.getSignatureConsentGivenAt());
    }

    @Transactional(readOnly = true)
    public Resource getImage(Authentication authentication) {
        User user = findUser(authentication);
        if (user.getSignatureFileName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No saved signature found");
        }
        try {
            Resource resource = new UrlResource(signatureDirectory.resolve(user.getSignatureFileName()).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Signature file not found");
            }
            return resource;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Signature file not found", ex);
        }
    }

    public byte[] loadBytes(User user) {
        if (user.getSignatureFileName() == null) return null;
        try {
            Path path = signatureDirectory.resolve(user.getSignatureFileName());
            return Files.exists(path) ? Files.readAllBytes(path) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    @Transactional
    public void saveFromContractIfAbsent(User user, byte[] signatureBytes, String contentType) {
        if (user.getSignatureFileName() != null || signatureBytes == null) return;
        String ext = resolveExtension(contentType);
        String fileName = user.getId() + "-profile-signature." + ext;
        try {
            Files.createDirectories(signatureDirectory);
            Files.write(signatureDirectory.resolve(fileName), signatureBytes);
        } catch (IOException ex) {
            return; // non-fatal; don't fail the signing operation
        }
        user.setSignatureFileName(fileName);
        user.setSignatureConsentGivenAt(ZonedDateTime.now());
        userRepository.save(user);
    }

    private User findUser(Authentication authentication) {
        return userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
