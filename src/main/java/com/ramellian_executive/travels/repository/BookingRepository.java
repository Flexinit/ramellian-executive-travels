package com.ramellian_executive.travels.repository;
import com.ramellian_executive.travels.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ramellian_executive.travels.entity.BookingStatus;

import java.util.UUID;
import java.time.ZonedDateTime;
import java.util.List;
@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("""
            select count(b) > 0 from Booking b
            where b.vehicle.id = :vehicleId
              and b.status in :activeStatuses
              and b.startTime < :endTime
              and b.endTime > :startTime
            """)
    boolean existsOverlappingBooking(@Param("vehicleId") UUID vehicleId,
                                     @Param("startTime") ZonedDateTime startTime,
                                     @Param("endTime") ZonedDateTime endTime,
                                     @Param("activeStatuses") List<BookingStatus> activeStatuses);

    @Query("""
            select count(b) > 0 from Booking b
            where b.chauffeur.id = :chauffeurId
              and b.status in :activeStatuses
              and b.startTime < :endTime
              and b.endTime > :startTime
              and b.id <> :bookingId
            """)
    boolean existsOverlappingChauffeurBooking(@Param("chauffeurId") UUID chauffeurId,
                                              @Param("bookingId") UUID bookingId,
                                              @Param("startTime") ZonedDateTime startTime,
                                              @Param("endTime") ZonedDateTime endTime,
                                              @Param("activeStatuses") List<BookingStatus> activeStatuses);

    List<Booking> findByUserEmailAddressIgnoreCaseOrderByStartTimeDesc(String email);
    List<Booking> findByVehicleOwnerEmailAddressIgnoreCaseOrderByStartTimeDesc(String email);
    List<Booking> findAllByOrderByStartTimeDesc();
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, ZonedDateTime cutoff);
}
