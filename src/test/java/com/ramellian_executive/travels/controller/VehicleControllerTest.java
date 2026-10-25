package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.controller.vehicle.VehicleController;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import com.ramellian_executive.travels.model.VehicleResponse;
import com.ramellian_executive.travels.model.VehicleReviewRequest;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock VehicleService vehicleService;
    @InjectMocks
    VehicleController vehicleController;

    @Test
    void listVehiclesDelegatesToService() {
        when(vehicleService.search(null, null, null, null, null, null, null, null, null, null, null, null, null, "recommended", 0, 20))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(0, vehicleController.listAvailableVehicles(null, null, null, null, null, null, null, null, null, null, null, null, null, "recommended", 0, 20).getContent().size());
    }

    @Test
    void reviewVehicleDelegatesToService() {
        UUID vehicleId = UUID.randomUUID();
        VehicleReviewRequest request = new VehicleReviewRequest("APPROVED", "Looks great");
        Authentication auth = mock(Authentication.class);
        VehicleResponse expected = mock(VehicleResponse.class);

        when(vehicleService.reviewVehicle(vehicleId, request, auth)).thenReturn(expected);

        VehicleResponse actual = vehicleController.reviewVehicle(vehicleId, request, auth);
        assertEquals(expected, actual);
        verify(vehicleService).reviewVehicle(vehicleId, request, auth);
    }

    @Test
    void getPendingVehiclesDelegatesToService() {
        when(vehicleService.listPendingVehicles()).thenReturn(List.of());
        assertEquals(0, vehicleController.getPendingVehicles().size());
        verify(vehicleService).listPendingVehicles();
    }

    @Test
    void getMyVehiclesDelegatesToService() {
        Authentication auth = mock(Authentication.class);
        when(vehicleService.listMyVehicles(auth)).thenReturn(List.of());
        assertEquals(0, vehicleController.getMyVehicles(auth).size());
        verify(vehicleService).listMyVehicles(auth);
    }

    @Test
    void getAllVehiclesForAdminDelegatesToService() {
        when(vehicleService.listAllVehiclesForAdmin(VehicleApprovalStatus.PENDING)).thenReturn(List.of());
        assertEquals(0, vehicleController.getAllVehiclesForAdmin(VehicleApprovalStatus.PENDING).size());
        verify(vehicleService).listAllVehiclesForAdmin(VehicleApprovalStatus.PENDING);
    }
}
