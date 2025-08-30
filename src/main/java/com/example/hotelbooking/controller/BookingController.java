package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private MeterRegistry meterRegistry;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        meterRegistry.counter("booking.create.requests").increment();
        
        Booking booking = bookingService.createBooking(request);
        
        return ResponseEntity.ok(convertToResponse(booking));
    }

    @PutMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> updateBooking(
            @PathVariable Long bookingId,
            @RequestBody BookingRequest request) {
        meterRegistry.counter("booking.update.requests").increment();
        
        Booking booking = bookingService.updateBooking(
                bookingId,
                request.getCheckInDate(),
                request.getCheckOutDate()
        );
        
        return ResponseEntity.ok(convertToResponse(booking));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        meterRegistry.counter("booking.cancel.requests").increment();
        bookingService.cancelBooking(bookingId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable Long userId) {
        meterRegistry.counter("booking.user.requests").increment();
        
        List<Booking> bookings = bookingService.getUserBookings(userId);
        List<BookingResponse> response = bookings.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long bookingId) {
        meterRegistry.counter("booking.get.requests").increment();
        
        Booking booking = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(convertToResponse(booking));
    }

    private BookingResponse convertToResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setId(booking.getId());
        response.setUserName(booking.getUser().getName());
        response.setHotelName(booking.getRoom().getHotel().getName());
        response.setRoomNumber(booking.getRoom().getRoomNumber());
        response.setCheckInDate(booking.getCheckInDate());
        response.setCheckOutDate(booking.getCheckOutDate());
        response.setTotalPrice(booking.getTotalPrice());
        response.setStatus(booking.getStatus());
        return response;
    }
}
