package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.*;
import com.ramellian_executive.travels.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.HexFormat;

@Service
public class PaymentService {
    private static final double PLATFORM_RATE = 0.01;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingContractRepository contractRepository;
    private final ContractTemplateService contractTemplateService;
    private final Path contractDirectory;
    private final boolean manualConfirmationEnabled;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                          BookingContractRepository contractRepository,
                          @Value("${app.contract.directory:uploads/contracts}") String contractDirectory,
                          ContractTemplateService contractTemplateService,
                          @Value("${app.payment.manual-confirmation-enabled:false}")
                          boolean manualConfirmationEnabled) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.contractDirectory = Paths.get(contractDirectory).toAbsolutePath().normalize();
        this.contractTemplateService = contractTemplateService;
        this.manualConfirmationEnabled = manualConfirmationEnabled;
    }

    @Transactional
    public ContractResponse confirm(UUID bookingId, PaymentConfirmationRequest request,
                                    Authentication authentication) {
        if (!manualConfirmationEnabled) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Manual payment confirmation is disabled; use Pesapal checkout");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can pay for this booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is not payable");
        }
        BookingContract contract = contractRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Review and accept the contract before payment"));
        if (contract.getAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Review and accept the contract before payment");
        }
        if (paymentRepository.existsByTransactionReference(request.transactionReference())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction reference already used");
        }
        if (paymentRepository.existsByBookingIdAndStatus(bookingId, PaymentStatus.SUCCESSFUL)) {
            return ContractResponse.from(contract);
        }
        long days = Math.max(1, java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toDays());
        double amount = booking.getVehicle().getPricePerDay() * days;
        double fee = amount * PLATFORM_RATE;
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod(request.paymentMethod().trim().toUpperCase());
        payment.setAmount(amount);
        payment.setPlatformFee(fee);
        payment.setOwnerPayout(amount - fee);
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setTransactionReference(request.transactionReference().trim());
        paymentRepository.save(payment);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse draft(UUID bookingId, Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        requireRenter(booking, authentication);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A contract draft can only be created for a pending booking");
        }
        return contractRepository.findByBookingId(bookingId)
                .map(ContractResponse::from)
                .orElseGet(() -> {
                    BookingContract contract = new BookingContract();
                    contract.setBooking(booking);
                    contract.setContractText(resolveContractText(booking));
                    contract.setTemplateVersion(1);
                    BookingContract saved = contractRepository.save(contract);
                    createPdf(saved);
                    return ContractResponse.from(saved);
                });
    }

    @Transactional
    public ContractResponse accept(UUID bookingId, ContractAcceptanceRequest request,
                                   Authentication authentication) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        requireRenter(booking, authentication);
        if (!request.accepted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract acceptance is required");
        }
        BookingContract contract = contractRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Create and review the contract before accepting it"));
        if (contract.getAcceptedAt() == null) {
            contract.setReviewedAt(java.time.ZonedDateTime.now());
            contract.setAcceptedAt(java.time.ZonedDateTime.now());
            contract.setAcceptedBy(authentication.getName());
            contractRepository.save(contract);
        }
        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse sign(UUID contractId, MultipartFile signature, boolean consent,
                                 Authentication authentication, String ipAddress, String userAgent) {
        BookingContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        if (!contract.getBooking().getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the renter can sign this contract");
        }
        if (!paymentRepository.existsByBookingIdAndStatus(contract.getBooking().getId(),
                PaymentStatus.SUCCESSFUL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Successful payment is required before signing");
        }
        if (contract.getStatus() != ContractStatus.PENDING_SIGNATURE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract is not awaiting signature");
        }
        if (!consent || signature.isEmpty() || signature.getContentType() == null
                || !signature.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A signature image and signing consent are required");
        }
        if (contract.getOriginalFileName() == null) createPdf(contract);
        String signedName = contract.getId() + "-signed.pdf";
        try (PDDocument document = Loader.loadPDF(contractDirectory.resolve(contract.getOriginalFileName()).toFile())) {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, signature.getBytes(), "signature");
            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                content.drawImage(image, 72, 100, 180, 70);
            }
            document.save(contractDirectory.resolve(signedName).toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signed contract could not be stored", ex);
        }
        contract.setStatus(ContractStatus.SIGNED);
        contract.setSignedAt(java.time.ZonedDateTime.now());
        contract.setSignedBy(authentication.getName());
        contract.setSignedFileName(signedName);
        contract.setSignerIpAddress(ipAddress);
        contract.setSignerUserAgent(userAgent);
        contract.setDocumentHash(hash(contractDirectory.resolve(signedName)));
        return ContractResponse.from(contractRepository.save(contract));
    }

    @Transactional(readOnly = true)
    public ContractResponse getContract(UUID contractId, Authentication authentication) {
        BookingContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        boolean renter = contract.getBooking().getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = contract.getBooking().getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        if (!renter && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this contract");
        }
        return ContractResponse.from(contract);
    }

    public Resource download(UUID contractId, boolean signed, Authentication authentication) {
        BookingContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));
        boolean renter = contract.getBooking().getUser().getEmailAddress().equalsIgnoreCase(authentication.getName());
        boolean owner = contract.getBooking().getVehicle().getOwner().getEmailAddress().equalsIgnoreCase(authentication.getName());
        if (!renter && !owner) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this contract");
        String name = signed ? contract.getSignedFileName() : contract.getOriginalFileName();
        if (name == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract document not found");
        try {
            Resource resource = new UrlResource(contractDirectory.resolve(name).toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract document not found");
            return resource;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract document not found", ex);
        }
    }

    private void createPdf(BookingContract contract) {
        try {
            Files.createDirectories(contractDirectory);
            String name = contract.getId() + "-original.pdf";
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 740);
                    for (String line : contract.getContractText().split("\\R")) {
                        content.showText(line.replaceAll("[^\\x20-\\x7E]", " "));
                        content.newLineAtOffset(0, -18);
                    }

                    content.endText();
                }
                document.save(contractDirectory.resolve(name).toFile());
            }
            contract.setOriginalFileName(name);
            contract.setDocumentHash(hash(contractDirectory.resolve(name)));
            contractRepository.save(contract);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Contract document could not be generated", ex);
        }
    }

    private String resolveContractText(Booking booking) {
        return contractTemplateService.resolveFor(booking)
                .replace("{{BOOKING_ID}}", booking.getId().toString())
                .replace("{{START_TIME}}", booking.getStartTime().toString())
                .replace("{{END_TIME}}", booking.getEndTime().toString())
                .replace("{{BOOKING_MODE}}", booking.getBookingMode().name())
                .replace("{{RENTER_EMAIL}}", booking.getUser().getEmailAddress())
                .replace("{{OWNER_EMAIL}}", booking.getVehicle().getOwner().getEmailAddress())
                .replace("{{VEHICLE_REGISTRATION}}", booking.getVehicle().getRegistrationNumber());
    }

    private void requireRenter(Booking booking, Authentication authentication) {
        if (!booking.getUser().getEmailAddress().equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the renter can access this contract");
        }
    }

    private String hash(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Contract document hash could not be generated", ex);
        }
    }
}
