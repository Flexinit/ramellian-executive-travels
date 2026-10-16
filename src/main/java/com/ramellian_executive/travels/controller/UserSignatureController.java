package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.SavedSignatureResponse;
import com.ramellian_executive.travels.service.UserSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/profile/signature")
public class UserSignatureController {

    private final UserSignatureService userSignatureService;

    @GetMapping
    public SavedSignatureResponse status(Authentication authentication) {
        return userSignatureService.status(authentication);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SavedSignatureResponse save(@RequestPart MultipartFile signature,
                                       @RequestParam boolean consent,
                                       Authentication authentication) {
        return userSignatureService.save(signature, consent, authentication);
    }

    @DeleteMapping
    public void remove(Authentication authentication) {
        userSignatureService.remove(authentication);
    }

    @GetMapping("/image")
    public ResponseEntity<Resource> image(Authentication authentication) {
        Resource resource = userSignatureService.getImage(authentication);
        String contentType = MediaType.IMAGE_PNG_VALUE;
        try {
            String filename = resource.getFilename();
            if (filename != null) {
                if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) contentType = MediaType.IMAGE_JPEG_VALUE;
                else if (filename.endsWith(".gif")) contentType = "image/gif";
                else if (filename.endsWith(".webp")) contentType = "image/webp";
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
    }
}
