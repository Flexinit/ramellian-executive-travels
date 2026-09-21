package com.ramellian_executive.travels.repository.vehicle;

import com.ramellian_executive.travels.entity.VehiclePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehiclePhotoRepository extends JpaRepository<VehiclePhoto, UUID> {
    List<VehiclePhoto> findByVehicleId(UUID vehicleId);
}
