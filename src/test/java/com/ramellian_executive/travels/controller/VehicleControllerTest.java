package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.controller.vehicle.VehicleController;
import com.ramellian_executive.travels.service.vehicle.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock VehicleService vehicleService;
    @InjectMocks
    VehicleController vehicleController;

    @Test
    void listVehiclesDelegatesToService() {
        when(vehicleService.search(null, null, null, null, null, null, null, null, null, null, null, null, "recommended"))
                .thenReturn(List.of());

        assertEquals(0, vehicleController.listAvailableVehicles(null, null, null, null, null, null, null, null, null, null, null, null, "recommended").size());
    }
}
