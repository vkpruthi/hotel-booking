package com.example.hotelbooking.service;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dao.BookingDao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class BookingService {
    private final BookingDao bookingDao;
    private final CacheService cacheService;

    public BookingService(BookingDao bookingDao, CacheService cacheService) {
        this.bookingDao = bookingDao;
        this.cacheService = cacheService;
    }

    public BookingService(BookingDao bookingDao) {
        this(bookingDao, new CacheService());
    }

    public Booking createBooking(BookingRequest request) {
        String availabilityKey = String.format("%d_%s_%s", 
            request.getRoomId(), 
            request.getCheckInDate(), 
            request.getCheckOutDate());

        // Check cache for room availability
        Boolean isAvailable = cacheService.getRoomAvailability(availabilityKey);
        if (isAvailable != null && !isAvailable) {
            throw new IllegalStateException("Room is not available for the selected dates");
        }

        // Double check with database if not in cache
        if (!bookingDao.isRoomAvailable(request.getRoomId(), request.getCheckInDate(), request.getCheckOutDate())) {
            cacheService.putRoomAvailability(availabilityKey, false);
            throw new IllegalStateException("Room is not available for the selected dates");
        }

        // Calculate total price
        long numberOfNights = ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal totalPrice = BigDecimal.valueOf(100.00).multiply(BigDecimal.valueOf(numberOfNights));

        // Create booking
        Booking booking = new Booking();
        booking.setCheckInDate(request.getCheckInDate());
        booking.setCheckOutDate(request.getCheckOutDate());
        booking.setTotalPrice(totalPrice);
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.setUser(bookingDao.findUserById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found")));
        booking.setRoom(bookingDao.findRoomById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found")));

        // Save and update caches
        Booking savedBooking = bookingDao.save(booking);
        cacheService.putBooking(savedBooking.getId(), savedBooking);
        cacheService.invalidateRoomAvailability(availabilityKey);
        cacheService.invalidateUserBookings(savedBooking.getUser().getId());
        
        return savedBooking;
    }

    public Booking updateBooking(Long bookingId, LocalDate newCheckInDate, LocalDate newCheckOutDate) {
        Booking booking = getBooking(bookingId); // This uses cache

        String availabilityKey = String.format("%d_%s_%s", 
            booking.getRoom().getId(), 
            newCheckInDate, 
            newCheckOutDate);

        // Check cache for room availability
        Boolean isAvailable = cacheService.getRoomAvailability(availabilityKey);
        if (isAvailable != null && !isAvailable) {
            throw new IllegalStateException("Room is not available for the new dates");
        }

        // Double check with database if not in cache
        if (!bookingDao.isRoomAvailable(booking.getRoom().getId(), newCheckInDate, newCheckOutDate)) {
            cacheService.putRoomAvailability(availabilityKey, false);
            throw new IllegalStateException("Room is not available for the new dates");
        }

        // Update dates and recalculate price
        booking.setCheckInDate(newCheckInDate);
        booking.setCheckOutDate(newCheckOutDate);
        
        long numberOfNights = ChronoUnit.DAYS.between(newCheckInDate, newCheckOutDate);
        BigDecimal totalPrice = BigDecimal.valueOf(100.00).multiply(BigDecimal.valueOf(numberOfNights));
        booking.setTotalPrice(totalPrice);

        // Save and update caches
        Booking updatedBooking = bookingDao.save(booking);
        cacheService.putBooking(bookingId, updatedBooking);
        cacheService.invalidateRoomAvailability(availabilityKey);
        cacheService.invalidateUserBookings(updatedBooking.getUser().getId());
        
        return updatedBooking;
    }

    public void cancelBooking(Long bookingId) {
        Booking booking = getBooking(bookingId); // This uses cache
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        
        // Save and update caches
        bookingDao.save(booking);
        cacheService.invalidateBooking(bookingId);
        cacheService.invalidateUserBookings(booking.getUser().getId());
        
        // Invalidate room availability cache for these dates
        String availabilityKey = String.format("%d_%s_%s", 
            booking.getRoom().getId(), 
            booking.getCheckInDate(), 
            booking.getCheckOutDate());
        cacheService.invalidateRoomAvailability(availabilityKey);
    }

    public List<Booking> getUserBookings(Long userId) {
        // Try to get from cache first
        List<Booking> cachedBookings = cacheService.getUserBookings(userId);
        if (cachedBookings != null) {
            return cachedBookings;
        }

        // If not in cache, get from DB and cache it
        List<Booking> bookings = bookingDao.findByUserId(userId);
        cacheService.putUserBookings(userId, bookings);
        return bookings;
    }

    public Booking getBooking(Long bookingId) {
        // Try to get from cache first
        Booking cachedBooking = cacheService.getBooking(bookingId);
        if (cachedBooking != null) {
            return cachedBooking;
        }

        // If not in cache, get from DB and cache it
        Booking booking = bookingDao.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        cacheService.putBooking(bookingId, booking);
        return booking;
    }
}
