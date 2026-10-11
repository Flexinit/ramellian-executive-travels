package com.ramellian_executive.travels.repository.vehicle;

import com.ramellian_executive.travels.entity.BookingStatus;
import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByAvailableTrue();

    List<Vehicle> findByAvailableTrueAndApprovalStatus(VehicleApprovalStatus approvalStatus);

    List<Vehicle> findByApprovalStatusOrderByCreatedAtDesc(VehicleApprovalStatus approvalStatus);

    List<Vehicle> findAllByOrderByCreatedAtDesc();

    List<Vehicle> findByOwnerEmailAddressIgnoreCase(String emailAddress);

    List<Vehicle> findByOwnerEmailAddressIgnoreCaseOrderByCreatedAtDesc(String emailAddress);

    default List<Vehicle> search(String query,
                                 String location,
                                 Double minPrice,
                                 Double maxPrice,
                                 Integer capacity,
                                 String fuelType,
                                 String transmissionType,
                                 Boolean selfDrive,
                                 Boolean chauffeured,
                                 UUID ownerId,
                                 Set<TripType> tripTypes,
                                 ZonedDateTime startTime,
                                 ZonedDateTime endTime,
                                 List<BookingStatus> bookingStatuses) {
        boolean filterTripTypes = tripTypes != null && !tripTypes.isEmpty();
        // Always apply the booking overlap check. When no range is given, default to
        // [now, now+1s] so vehicles with a live booking at this instant are excluded.
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime effectiveStart = startTime != null ? startTime : now;
        ZonedDateTime effectiveEnd   = endTime   != null ? endTime   : now.plusSeconds(1);
        return searchInternal(
                query, location, minPrice, maxPrice, capacity,
                fuelType, transmissionType, selfDrive, chauffeured, ownerId,
                filterTripTypes,
                filterTripTypes ? tripTypes : EnumSet.allOf(TripType.class),
                false,
                effectiveStart,
                effectiveEnd,
                (bookingStatuses == null || bookingStatuses.isEmpty())
                        ? List.of(BookingStatus.values())
                        : bookingStatuses);
    }

    @Query("""
            select distinct b.vehicle.id from Booking b
            where b.status in :statuses
              and b.startTime < :endTime
              and b.endTime > :startTime
            """)
    List<UUID> findBookedVehicleIds(@Param("statuses") List<BookingStatus> statuses,
                                    @Param("startTime") ZonedDateTime startTime,
                                    @Param("endTime") ZonedDateTime endTime);

    @Query("""
            select distinct v from Vehicle v
            left join v.tripTypes tt
            where v.available = true
              and v.approvalStatus = com.ramellian_executive.travels.entity.VehicleApprovalStatus.APPROVED
              and (:query is null
                   or lower(v.model)            like lower(concat('%', cast(:query as String), '%'))
                   or lower(v.location)         like lower(concat('%', cast(:query as String), '%'))
                   or lower(v.color)            like lower(concat('%', cast(:query as String), '%'))
                   or lower(v.fuelType)         like lower(concat('%', cast(:query as String), '%'))
                   or lower(v.transmissionType) like lower(concat('%', cast(:query as String), '%')))
              and (:location is null or lower(v.location) like lower(concat('%', cast(:location as String), '%')))
              and (:minPrice is null or v.pricePerDay >= :minPrice)
              and (:maxPrice is null or v.pricePerDay <= :maxPrice)
              and (:capacity is null or v.capacity >= :capacity)
              and (:fuelType is null or lower(v.fuelType) = lower(cast(:fuelType as String)))
              and (:transmissionType is null or lower(v.transmissionType) = lower(cast(:transmissionType as String)))
              and (:selfDrive is null or v.selfDrive = :selfDrive)
              and (:chauffeured is null or v.chauffeured = :chauffeured)
              and (:ownerId is null or v.owner.id = :ownerId)
              and (:filterTripTypes = false or tt in :tripTypes)
              and (:filterTime = false or not exists (
                  select b.id from Booking b
                  where b.vehicle = v
                    and b.status in :bookingStatuses
                    and b.startTime < :endTime
                    and b.endTime > :startTime
              ))
            """)
    List<Vehicle> searchInternal(@Param("query") String query,
                                 @Param("location") String location,
                                 @Param("minPrice") Double minPrice,
                                 @Param("maxPrice") Double maxPrice,
                                 @Param("capacity") Integer capacity,
                                 @Param("fuelType") String fuelType,
                                 @Param("transmissionType") String transmissionType,
                                 @Param("selfDrive") Boolean selfDrive,
                                 @Param("chauffeured") Boolean chauffeured,
                                 @Param("ownerId") UUID ownerId,
                                 @Param("filterTripTypes") boolean filterTripTypes,
                                 @Param("tripTypes") Set<TripType> tripTypes,
                                 @Param("filterTime") boolean filterTime,
                                 @Param("startTime") ZonedDateTime startTime,
                                 @Param("endTime") ZonedDateTime endTime,
                                 @Param("bookingStatuses") List<BookingStatus> bookingStatuses);
}