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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.HexFormat;

@Service
public class PaymentService {
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ContractTemplateService contractTemplateService;
    private final PlatformSettingsService platformSettingsService;
    private final ReceiptService receiptService;
    private final UserSignatureService userSignatureService;
    private final com.ramellian_executive.travels.security.VerificationNotificationService notificationService;
    private final NotificationService appNotificationService;
    private final Path contractDirectory;
    private final boolean manualConfirmationEnabled;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
                          BookingContractRepository contractRepository,
                          UserRepository userRepository,
                          @Value("${app.contract.directory:uploads/contracts}") String contractDirectory,
                          ContractTemplateService contractTemplateService,
                          PlatformSettingsService platformSettingsService,
                          ReceiptService receiptService,
                          UserSignatureService userSignatureService,
                          com.ramellian_executive.travels.security.VerificationNotificationService notificationService,
                          NotificationService appNotificationService,
                          @Value("${app.payment.manual-confirmation-enabled:false}")
                          boolean manualConfirmationEnabled) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.contractDirectory = Paths.get(contractDirectory).toAbsolutePath().normalize();
        this.contractTemplateService = contractTemplateService;
        this.platformSettingsService = platformSettingsService;
        this.receiptService = receiptService;
        this.userSignatureService = userSignatureService;
        this.notificationService = notificationService;
        this.appNotificationService = appNotificationService;
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
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double fee = base * (platformSettingsService.getCommissionRatePct() / 100.0);
        double txFee = base * (platformSettingsService.getPesapalTransactionFeeRatePct() / 100.0);
        double deliveryFee = booking.isDeliveryRequested() ? platformSettingsService.getDeliveryFlatFee() : 0.0;
        double driverCharge = booking.getBookingMode() == BookingMode.CHAUFFEURED
                ? platformSettingsService.getDriverFlatCharge() : 0.0;
        double totalCharge = base + txFee + deliveryFee + driverCharge;
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod(request.paymentMethod().trim().toUpperCase());
        payment.setAmount(totalCharge);
        payment.setPlatformFee(fee);
        payment.setTransactionFee(txFee);
        payment.setDeliveryFee(deliveryFee);
        payment.setDriverCharge(driverCharge);
        payment.setOwnerPayout(base - fee);
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment.setTransactionReference(request.transactionReference().trim());
        Payment saved = paymentRepository.save(payment);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        Payment withReceipt = receiptService.generate(saved);
        notificationService.sendPaymentReceipt(withReceipt);

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse draft(UUID bookingId, Authentication authentication) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
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
    public ContractResponse signAgreement(UUID contractId, MultipartFile signature,
                                          boolean useSavedSignature, boolean consent,
                                          boolean saveAsProfile,
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
        if (!consent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signing consent is required");
        }

        User renter = userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        byte[] signatureBytes;
        String signatureContentType;
        if (useSavedSignature) {
            if (renter.getSignatureFileName() == null || renter.getSignatureConsentGivenAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No saved signature found. Please save your signature in your profile first");
            }
            signatureBytes = userSignatureService.loadBytes(renter);
            if (signatureBytes == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Saved signature file could not be loaded. Please re-upload your profile signature");
            }
            signatureContentType = renter.getSignatureFileName().endsWith(".png") ? "image/png" : "image/jpeg";
        } else {
            if (signature == null || signature.isEmpty() || signature.getContentType() == null
                    || !signature.getContentType().startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A signature image is required when not using a saved signature");
            }
            try {
                signatureBytes = signature.getBytes();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read signature");
            }
            signatureContentType = signature.getContentType();
        }

        if (contract.getOriginalFileName() == null) createPdf(contract);

        String signedName = contract.getId() + "-signed.pdf";
        String signerLabel = renter.getFirstName() != null ? renter.getFirstName() : authentication.getName();
        String signedTimestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z"));

        try (PDDocument document = Loader.loadPDF(contractDirectory.resolve(contract.getOriginalFileName()).toFile())) {
            PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, signatureBytes, "renter-sig");
            try (PDPageContentStream content = new PDPageContentStream(document, lastPage,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                // renter signature block — bottom left
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                content.beginText();
                content.newLineAtOffset(72, 95);
                content.showText("Renter: " + signerLabel);
                content.newLineAtOffset(0, -10);
                content.showText(signedTimestamp);
                content.endText();
                content.drawImage(image, 72, 110, 160, 60);
            }
            document.save(contractDirectory.resolve(signedName).toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signed contract could not be stored", ex);
        }

        contract.setStatus(ContractStatus.SIGNED);
        contract.setSignedAt(ZonedDateTime.now());
        contract.setSignedBy(authentication.getName());
        contract.setSignedFileName(signedName);
        contract.setSignerIpAddress(ipAddress);
        contract.setSignerUserAgent(userAgent);
        contract.setRenterSignatureFileName(useSavedSignature ? renter.getSignatureFileName() : null);
        contract.setDocumentHash(hash(contractDirectory.resolve(signedName)));
        contractRepository.save(contract);

        // Optionally save this drawing as the user's profile signature
        if (!useSavedSignature && saveAsProfile) {
            userSignatureService.saveFromContractIfAbsent(renter, signatureBytes, signatureContentType);
        }

        notifyOwnerToSign(contract);

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse ownerSignAgreement(UUID contractId, MultipartFile signature,
                                               boolean useSavedSignature, boolean consent,
                                               Authentication authentication, String ipAddress, String userAgent) {
        BookingContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found"));

        String ownerEmail = contract.getBooking().getVehicle().getOwner().getEmailAddress();
        if (!ownerEmail.equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the vehicle owner can sign as owner");
        }
        if (contract.getStatus() != ContractStatus.SIGNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The renter must sign first before the owner can sign");
        }
        if (contract.getOwnerSignedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner has already signed this contract");
        }
        if (!consent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signing consent is required");
        }

        User owner = userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        byte[] signatureBytes;
        if (useSavedSignature) {
            if (owner.getSignatureFileName() == null || owner.getSignatureConsentGivenAt() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No saved signature found. Please save your signature in your profile first");
            }
            signatureBytes = userSignatureService.loadBytes(owner);
            if (signatureBytes == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Saved signature file could not be loaded. Please re-upload your profile signature");
            }
        } else {
            if (signature == null || signature.isEmpty() || signature.getContentType() == null
                    || !signature.getContentType().startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A signature image is required when not using a saved signature");
            }
            try {
                signatureBytes = signature.getBytes();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read signature");
            }
        }

        String ownerLabel = owner.getFirstName() != null ? owner.getFirstName() : authentication.getName();
        String signedTimestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z"));
        String signedName = contract.getSignedFileName(); // append to existing renter-signed PDF

        try (PDDocument document = Loader.loadPDF(contractDirectory.resolve(signedName).toFile())) {
            PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, signatureBytes, "owner-sig");
            try (PDPageContentStream content = new PDPageContentStream(document, lastPage,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                // owner signature block — bottom right
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                content.beginText();
                content.newLineAtOffset(320, 95);
                content.showText("Owner: " + ownerLabel);
                content.newLineAtOffset(0, -10);
                content.showText(signedTimestamp);
                content.endText();
                content.drawImage(image, 320, 110, 160, 60);
            }
            document.save(contractDirectory.resolve(signedName).toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Signed contract could not be stored", ex);
        }

        contract.setStatus(ContractStatus.FULLY_SIGNED);
        contract.setOwnerSignedAt(ZonedDateTime.now());
        contract.setOwnerSignedBy(authentication.getName());
        contract.setOwnerSignerIpAddress(ipAddress);
        contract.setOwnerSignerUserAgent(userAgent);
        contract.setOwnerSignatureFileName(useSavedSignature ? owner.getSignatureFileName() : null);
        contract.setDocumentHash(hash(contractDirectory.resolve(signedName)));
        BookingContract saved = contractRepository.save(contract);

        notificationService.sendFullySignedContractCopies(saved, contractDirectory);

        return ContractResponse.from(saved);
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
        Vehicle vehicle = booking.getVehicle();
        User renter   = booking.getUser();
        User owner    = vehicle.getOwner();

        // Pickup/dropoff: use booking-specific location if set, else vehicle base location
        String pickup  = safe(booking.getPickupLocation(),  vehicle.getLocation());
        String dropoff = safe(booking.getDropoffLocation(), vehicle.getLocation());

        // Owner identity: prefer business name, fall back to personal name
        String ownerName = owner.getBusinessName() != null && !owner.getBusinessName().isBlank()
                ? owner.getBusinessName()
                : fullName(owner);

        String bookedOn = booking.getCreatedAt() != null
                ? booking.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a z"))
                : "—";

        return contractTemplateService.resolveFor(booking)
                // Booking
                .replace("{{BOOKING_ID}}",           booking.getId().toString())
                .replace("{{START_TIME}}",            booking.getStartTime().toString())
                .replace("{{END_TIME}}",              booking.getEndTime().toString())
                .replace("{{BOOKING_MODE}}",          booking.getBookingMode().name())
                .replace("{{TRIP_PURPOSE}}",          booking.getTripType() != null ? booking.getTripType().name() : "—")
                .replace("{{PICKUP_LOCATION}}",       pickup)
                .replace("{{DROPOFF_LOCATION}}",      dropoff)
                .replace("{{BOOKED_ON}}",             bookedOn)
                // Renter
                .replace("{{RENTER_NAME}}",           fullName(renter))
                .replace("{{RENTER_EMAIL}}",          safe(renter.getEmailAddress()))
                .replace("{{RENTER_PHONE}}",          safe(renter.getPhoneNumber()))
                .replace("{{RENTER_ID_NUMBER}}",      safe(renter.getIdNumber()))
                .replace("{{RENTER_LOCATION}}",       safe(renter.getLocation()))
                // Owner
                .replace("{{OWNER_NAME}}",            ownerName)
                .replace("{{OWNER_PERSONAL_NAME}}",   fullName(owner))
                .replace("{{OWNER_BUSINESS_NAME}}",   safe(owner.getBusinessName()))
                .replace("{{OWNER_KRA_PIN}}",         safe(owner.getBusinessTaxNumber()))
                .replace("{{OWNER_LOCATION}}",        safe(owner.getBusinessAddress(), owner.getLocation()))
                .replace("{{OWNER_EMAIL}}",           safe(owner.getEmailAddress()))
                .replace("{{OWNER_PHONE}}",           safe(owner.getPhoneNumber()))
                // Vehicle
                .replace("{{VEHICLE_REGISTRATION}}", safe(vehicle.getRegistrationNumber()))
                .replace("{{VEHICLE_VIN}}",           safe(vehicle.getVIN()))
                .replace("{{VEHICLE_MODEL}}",         safe(vehicle.getModel()))
                .replace("{{VEHICLE_COLOR}}",         safe(vehicle.getColor()))
                .replace("{{VEHICLE_FUEL_TYPE}}",     safe(vehicle.getFuelType()))
                .replace("{{VEHICLE_TRANSMISSION}}",  safe(vehicle.getTransmissionType()))
                .replace("{{VEHICLE_CAPACITY}}",      String.valueOf(vehicle.getCapacity()))
                .replace("{{VEHICLE_YEAR}}",          vehicle.getYear() != null ? String.valueOf(vehicle.getYear()) : "—")
                .replace("{{VEHICLE_INSURER}}",       safe(vehicle.getInsurer()))
                .replace("{{VEHICLE_POLICY_NUMBER}}", safe(vehicle.getPolicyNumber()))
                // Renter licence & booking extras
                .replace("{{RENTER_LICENCE_NUMBER}}", safe(renter.getDrivingLicenceNumber()))
                .replace("{{PASSENGER_COUNT}}",       booking.getPassengerCount() != null ? String.valueOf(booking.getPassengerCount()) : "—");
    }

    private static String safe(String value) {
        return (value != null && !value.isBlank()) ? value : "—";
    }

    private static String safe(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return "—";
    }

    private static String fullName(User user) {
        String first  = user.getFirstName()  != null ? user.getFirstName().trim()  : "";
        String middle = user.getMiddleName() != null ? user.getMiddleName().trim() : "";
        String last   = user.getLastName()   != null ? user.getLastName().trim()   : "";
        String name   = (first + " " + middle + " " + last).replaceAll("\\s+", " ").trim();
        return name.isBlank() ? "—" : name;
    }

    private void notifyOwnerToSign(BookingContract contract) {
        try {
            User owner = contract.getBooking().getVehicle().getOwner();
            if (owner == null) return;
            String vehicleTitle = contract.getBooking().getVehicle().getModel() != null
                    ? contract.getBooking().getVehicle().getModel() : "your vehicle";
            appNotificationService.createNotification(
                    owner,
                    "Action Required: Sign Rental Contract",
                    "The renter has signed the contract for " + vehicleTitle
                            + ". Please review and sign it to confirm the booking.",
                    "CONTRACT_OWNER_SIGN_REQUIRED",
                    contract.getId(),
                    "CONTRACT"
            );
            notificationService.sendOwnerContractSignRequest(contract);
        } catch (Exception e) {
            // notification failure must not roll back the signing
        }
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
