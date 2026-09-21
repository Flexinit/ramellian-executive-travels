package com.ramellian_executive.travels.repository.vehicle;

import com.ramellian_executive.travels.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.time.ZonedDateTime;
import com.ramellian_executive.travels.entity.BookingStatus;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByAvailableTrue();

    @Query("""
            select distinct v from Vehicle v
            left join v.tripTypes tt
            where v.available = true
              and (:query is null or lower(v.model) like lower(concat('%', :query, '%'))
                   or lower(v.location) like lower(concat('%', :query, '%'))
                   or lower(v.color) like lower(concat('%', :query, '%'))
                   or lower(v.fuelType) like lower(concat('%', :query, '%'))
                   or lower(v.transmissionType) like lower(concat('%', :query, '%')))
              and (:location is null or lower(v.location) like lower(concat('%', :location, '%')))
              and (:minPrice is null or v.pricePerDay >= :minPrice)
              and (:maxPrice is null or v.pricePerDay <= :maxPrice)
              and (:capacity is null or v.capacity >= :capacity)
              and (:fuelType is null or lower(v.fuelType) = lower(:fuelType))
              and (:transmissionType is null or lower(v.transmissionType) = lower(:transmissionType))
              and (:selfDrive is null or v.selfDrive = :selfDrive)
              and (:chauffeured is null or v.chauffeured = :chauffeured)
              and (:tripTypes is null or tt in :tripTypes)
              and (:startTime is null or :endTime is null or not exists (
                  select b.id from Booking b
                  where b.vehicle = v
                    and b.status in :bookingStatuses
                    and b.startTime < :endTime
                    and b.endTime > :startTime
              ))
            """)
    List<Vehicle> search(@Param("query") String query,
                         @Param("location") String location,
                         @Param("minPrice") Double minPrice,
                         @Param("maxPrice") Double maxPrice,
                         @Param("capacity") Integer capacity,
                         @Param("fuelType") String fuelType,
                         @Param("transmissionType") String transmissionType,
                         @Param("selfDrive") Boolean selfDrive,
                         @Param("chauffeured") Boolean chauffeured,
                         @Param("tripTypes") java.util.Set<com.ramellian_executive.travels.entity.TripType> tripTypes,
                         @Param("startTime") ZonedDateTime startTime,
                         @Param("endTime") ZonedDateTime endTime,
                         @Param("bookingStatuses") List<BookingStatus> bookingStatuses);

    List<Vehicle> findByOwnerEmailAddressIgnoreCase(String emailAddress);
}
