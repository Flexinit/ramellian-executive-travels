package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.BookingLead;
import com.ramellian_executive.travels.model.BookingLeadRequest;
import com.ramellian_executive.travels.model.BookingLeadResponse;
import com.ramellian_executive.travels.repository.BookingLeadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BookingLeadService {
    private final BookingLeadRepository bookingLeadRepository;

    public BookingLeadService(BookingLeadRepository bookingLeadRepository) {
        this.bookingLeadRepository = bookingLeadRepository;
    }

    public BookingLeadResponse create(BookingLeadRequest request) {
        if ((request.email() == null || request.email().isBlank())
                && (request.phoneNumber() == null || request.phoneNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one of email or phone number must be provided");
        }
        BookingLead lead = new BookingLead();
        lead.setEmail(request.email());
        lead.setPhoneNumber(request.phoneNumber());
        lead.setVehicleId(request.vehicleId());
        lead.setStartTime(request.startTime());
        lead.setEndTime(request.endTime());
        lead.setNotes(request.notes());
        return BookingLeadResponse.from(bookingLeadRepository.save(lead));
    }
}
