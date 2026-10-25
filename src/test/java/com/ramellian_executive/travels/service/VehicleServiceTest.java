package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import com.ramellian_executive.travels.model.VehicleRequest;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.VehicleReviewRequest;
import com.ramellian_executive.travels.repository.UserRepository;
import com.ramellian_executive.travels.repository.vehicle.VehiclePhotoRepository;
import com.ramellian_executive.travels.repository.vehicle.VehicleRepository;
import com.ramellian_executive.travels.security.VerificationNotificationService;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock VehiclePhotoRepository photoRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock VerificationNotificationService emailNotificationService;
    @Mock AuditLogService auditLogService;
    @Mock Authentication authentication;

    VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, photoRepository, userRepository, notificationService, emailNotificationService, auditLogService);
    }

    @Test
    void createVehicleAssignsAuthenticatedOwnerAndAllFields() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle saved = new Vehicle();
        saved.setModel("Toyota");
        saved.setOwner(owner);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        java.time.ZonedDateTime from = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime to = from.plusDays(10);
        java.util.Set<com.ramellian_executive.travels.entity.TripType> tripTypes = java.util.Set.of(com.ramellian_executive.travels.entity.TripType.WEDDING);

        VehicleRequest request = new VehicleRequest(
                "Toyota Land Cruiser", "VIN123", "KDA123A", "Nairobi", 150.0,
                7, "Black", "PETROL", "AUTOMATIC", true, true, tripTypes,
                true, from, to, true, true, "http://example.com/sticker.jpg", null, null, null);

        VehicleResponse response = vehicleService.createVehicle(request, authentication);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle captured = captor.getValue();
        assertSame(owner, captured.getOwner());
        assertEquals("Toyota Land Cruiser", captured.getModel());
        assertEquals("VIN123", captured.getVIN());
        assertEquals("KDA123A", captured.getRegistrationNumber());
        assertEquals("Nairobi", captured.getLocation());
        assertEquals(150.0, captured.getPricePerDay());
        assertEquals(7, captured.getCapacity());
        assertEquals("Black", captured.getColor());
        assertEquals("PETROL", captured.getFuelType());
        assertEquals("AUTOMATIC", captured.getTransmissionType());
        assertTrue(captured.isSelfDrive());
        assertTrue(captured.isChauffeured());
        assertFalse(captured.isAvailable()); // newly created vehicle cannot be available until approved
        assertEquals(VehicleApprovalStatus.PENDING, captured.getApprovalStatus());
        assertEquals(from, captured.getAvailableFrom());
        assertEquals(to, captured.getAvailableTo());
        assertTrue(captured.isHasWorkingAC());
        assertTrue(captured.isComprehensiveInsurance());
        assertEquals("http://example.com/sticker.jpg", captured.getInsuranceVerificationStickerPhoto());
        assertEquals(tripTypes, captured.getTripTypes());

        assertEquals("Toyota Land Cruiser", response.model());
        assertFalse(response.available());
        assertEquals(VehicleApprovalStatus.PENDING, response.approvalStatus());
        assertTrue(response.hasWorkingAC());
        assertTrue(response.comprehensiveInsurance());
        assertEquals("http://example.com/sticker.jpg", response.insuranceVerificationStickerPhoto());
    }

    @Test
    void updateVehicleUpdatesAllFields() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setApprovalStatus(VehicleApprovalStatus.APPROVED);
        UUID id = UUID.randomUUID();
        vehicle.setId(id);

        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("owner@example.com");
        when(authentication.getAuthorities()).thenReturn(java.util.Collections.emptyList());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        java.time.ZonedDateTime from = java.time.ZonedDateTime.now();
        java.time.ZonedDateTime to = from.plusDays(5);
        java.util.Set<com.ramellian_executive.travels.entity.TripType> tripTypes = java.util.Set.of(com.ramellian_executive.travels.entity.TripType.AIRPORT_TRANSFER);

        VehicleRequest request = new VehicleRequest(
                "Mercedes S-Class", "VIN999", "KDB456Z", "Mombasa", 250.0,
                4, "Silver", "DIESEL", "AUTOMATIC", false, true, tripTypes,
                true, from, to, true, true, "http://example.com/new-sticker.jpg", null, null, null);

        VehicleResponse response = vehicleService.updateVehicle(id, request, authentication);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle captured = captor.getValue();
        assertEquals("Mercedes S-Class", captured.getModel());
        assertEquals("VIN999", captured.getVIN());
        assertEquals("KDB456Z", captured.getRegistrationNumber());
        assertEquals("Mombasa", captured.getLocation());
        assertEquals(250.0, captured.getPricePerDay());
        assertEquals(4, captured.getCapacity());
        assertEquals("Silver", captured.getColor());
        assertEquals("DIESEL", captured.getFuelType());
        assertEquals("AUTOMATIC", captured.getTransmissionType());
        assertFalse(captured.isSelfDrive());
        assertTrue(captured.isChauffeured());
        assertTrue(captured.isAvailable());
        assertEquals(from, captured.getAvailableFrom());
        assertEquals(to, captured.getAvailableTo());
        assertTrue(captured.isHasWorkingAC());
        assertTrue(captured.isComprehensiveInsurance());
        assertEquals("http://example.com/new-sticker.jpg", captured.getInsuranceVerificationStickerPhoto());
        assertEquals(tripTypes, captured.getTripTypes());

        assertEquals("Mercedes S-Class", response.model());
    }

    @Test
    void updateVehicleRejectsAvailableTrueWhenNotApproved() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setApprovalStatus(VehicleApprovalStatus.PENDING);
        UUID id = UUID.randomUUID();
        vehicle.setId(id);

        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("owner@example.com");
        when(authentication.getAuthorities()).thenReturn(java.util.Collections.emptyList());

        VehicleRequest request = new VehicleRequest(
                "Mercedes S-Class", "VIN999", "KDB456Z", "Mombasa", 250.0,
                4, "Silver", "DIESEL", "AUTOMATIC", false, true, null,
                true, null, null, true, true, null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> vehicleService.updateVehicle(id, request, authentication));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Vehicle must be approved"));
    }

    @Test
    void reviewVehicleApprovesAndEnablesAvailability() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Prado");
        vehicle.setRegistrationNumber("KDD111");
        vehicle.setOwner(owner);
        vehicle.setApprovalStatus(VehicleApprovalStatus.PENDING);
        vehicle.setAvailable(false);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("admin@ramellian.com");
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        VehicleReviewRequest reviewRequest = new VehicleReviewRequest("APPROVED", "All docs valid");
        VehicleResponse response = vehicleService.reviewVehicle(vehicle.getId(), reviewRequest, authentication);

        assertEquals(VehicleApprovalStatus.APPROVED, response.approvalStatus());
        assertTrue(response.available());
        assertEquals("All docs valid", response.approvalNotes());
        assertEquals("admin@ramellian.com", response.reviewedBy());
        assertNotNull(response.reviewedAt());

        verify(notificationService).createNotification(eq(owner), contains("Approved"), anyString(), eq("VEHICLE_APPROVED"), eq(vehicle.getId()), eq("VEHICLE"));
        verify(emailNotificationService).sendVehicleReviewNotification(vehicle);
        verify(auditLogService).logAction(eq(authentication), eq("VEHICLE_APPROVAL"), eq("VEHICLE"), eq(vehicle.getId().toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void reviewVehicleRejectsAndDisablesAvailability() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Prado");
        vehicle.setRegistrationNumber("KDD111");
        vehicle.setOwner(owner);
        vehicle.setApprovalStatus(VehicleApprovalStatus.PENDING);
        vehicle.setAvailable(true);

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("admin@ramellian.com");
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        VehicleReviewRequest reviewRequest = new VehicleReviewRequest("REJECTED", "Insurance sticker unreadable");
        VehicleResponse response = vehicleService.reviewVehicle(vehicle.getId(), reviewRequest, authentication);

        assertEquals(VehicleApprovalStatus.REJECTED, response.approvalStatus());
        assertFalse(response.available());
        assertEquals("Insurance sticker unreadable", response.approvalNotes());

        verify(notificationService).createNotification(eq(owner), contains("Rejected"), anyString(), eq("VEHICLE_REJECTED"), eq(vehicle.getId()), eq("VEHICLE"));
        verify(emailNotificationService).sendVehicleReviewNotification(vehicle);
        verify(auditLogService).logAction(eq(authentication), eq("VEHICLE_REJECTION"), eq("VEHICLE"), eq(vehicle.getId().toString()), anyString(), eq("SUCCESS"));
    }

    @Test
    void listPendingVehiclesReturnsPending() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setApprovalStatus(VehicleApprovalStatus.PENDING);
        when(vehicleRepository.findByApprovalStatusOrderByCreatedAtDesc(VehicleApprovalStatus.PENDING))
                .thenReturn(List.of(vehicle));

        List<VehicleResponse> pending = vehicleService.listPendingVehicles();
        assertEquals(1, pending.size());
        assertEquals(VehicleApprovalStatus.PENDING, pending.get(0).approvalStatus());
    }

    @Test
    void getMissingVehicleReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> vehicleService.getVehicle(id));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void ownerCannotUpdateAnotherOwnersVehicle() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");
        User other = new User();
        other.setEmailAddress("other@example.com");
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(other);
        when(vehicleRepository.findById(any())).thenReturn(Optional.of(vehicle));
        when(authentication.getName()).thenReturn("owner@example.com");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> vehicleService.updateVehicle(UUID.randomUUID(), new VehicleRequest(
                        "Toyota", "VIN123", "KDA123A", "Nairobi", 100.0,
                        5, "Black", "PETROL", "AUTOMATIC", true, false, null, true, null, null, true, true, null, null, null, null), authentication));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void searchDelegatesToVehicleRepository() {
        User owner = new User();
        owner.setEmailAddress("owner@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Toyota Land Cruiser");
        vehicle.setPricePerDay(150.0);
        vehicle.setCapacity(7);
        vehicle.setAvailable(true);
        vehicle.setApprovalStatus(VehicleApprovalStatus.APPROVED);
        vehicle.setOwner(owner);

        when(vehicleRepository.findBookedVehicleIds(any(), any(), any())).thenReturn(List.of());
        when(vehicleRepository.search(
                eq("Toyota"), eq("Nairobi"), eq(100.0), eq(200.0), eq(5),
                eq("PETROL"), eq("AUTOMATIC"), eq(true), eq(false), isNull(),
                isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(vehicle)));

        java.util.List<VehicleResponse> results = vehicleService.search(
                "Toyota", "Nairobi", 100.0, 200.0, 5,
                "PETROL", "AUTOMATIC", true, false, null,
                null, null, null, "price_asc", 0, 20).getContent();

        assertEquals(1, results.size());
        assertEquals("Toyota Land Cruiser", results.get(0).model());
    }

    @Test
    void searchFiltersByOwnerId() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);
        owner.setEmailAddress("owner@example.com");

        Vehicle vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Toyota RAV4");
        vehicle.setPricePerDay(120.0);
        vehicle.setCapacity(5);
        vehicle.setAvailable(true);
        vehicle.setApprovalStatus(VehicleApprovalStatus.APPROVED);
        vehicle.setOwner(owner);

        when(vehicleRepository.findBookedVehicleIds(any(), any(), any())).thenReturn(List.of());
        when(vehicleRepository.search(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(ownerId),
                isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(vehicle)));

        java.util.List<VehicleResponse> results = vehicleService.search(
                null, null, null, null, null,
                null, null, null, null, ownerId,
                null, null, null, "recommended", 0, 20).getContent();

        assertEquals(1, results.size());
        assertEquals("Toyota RAV4", results.get(0).model());
    }
}
