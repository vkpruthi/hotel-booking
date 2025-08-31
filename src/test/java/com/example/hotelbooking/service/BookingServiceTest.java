package com.example.hotelbooking.service;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.User;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dao.BookingDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import org.mockito.InjectMocks;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BookingServiceTest {
    @Mock
    private BookingDao bookingDao;
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private BookingService bookingService;
    
    private User testUser;
    private Room testRoom;
    private Hotel testHotel;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up test data
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");

        testHotel = new Hotel();
        testHotel.setId(1L);
        testHotel.setName("Test Hotel");
        testHotel.setAddress("Test Address");
        testHotel.setRating(4);

        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setRoomNumber("101");
        testRoom.setRoomType("STANDARD");
        testRoom.setPricePerNight(new BigDecimal("100.00"));
        testRoom.setHotel(testHotel);
        
        // Setup common mock behavior
        when(bookingDao.findUserById(1L)).thenReturn(Optional.of(testUser));
        when(bookingDao.findRoomById(1L)).thenReturn(Optional.of(testRoom));
        
        // Setup cache behavior
        when(cacheService.getRoomAvailability(any())).thenReturn(null); // force DB check
        when(cacheService.getBooking(any())).thenReturn(null); // force DB check
    }

    @Test
    void createBooking_Success() {
        // Setup
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        
        Booking expectedBooking = new Booking();
        expectedBooking.setId(1L);
        expectedBooking.setUser(testUser);
        expectedBooking.setRoom(testRoom);
        expectedBooking.setCheckInDate(checkIn);
        expectedBooking.setCheckOutDate(checkOut);
        expectedBooking.setStatus(Booking.BookingStatus.CONFIRMED);
        expectedBooking.setTotalPrice(new BigDecimal("200.00"));

        when(cacheService.getRoomAvailability(any())).thenReturn(null); // force DB check
        when(bookingDao.isRoomAvailable(any(), any(), any())).thenReturn(true);
        when(bookingDao.save(any())).thenReturn(expectedBooking);

        // Execute
        BookingRequest request = new BookingRequest();
        request.setUserId(testUser.getId());
        request.setRoomId(testRoom.getId());
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);

        Booking booking = bookingService.createBooking(request);

        // Verify
        assertNotNull(booking);
        assertEquals(expectedBooking.getId(), booking.getId());
        assertEquals(expectedBooking.getCheckInDate(), booking.getCheckInDate());
        assertEquals(expectedBooking.getCheckOutDate(), booking.getCheckOutDate());
        assertEquals(expectedBooking.getStatus(), booking.getStatus());
        assertEquals(expectedBooking.getTotalPrice(), booking.getTotalPrice());
    }

    @Test
    void createBooking_RoomNotAvailable() {
        // Setup
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);
        
        when(bookingDao.isRoomAvailable(any(), any(), any())).thenReturn(false);

        // Execute & Verify
        BookingRequest request = new BookingRequest();
        request.setUserId(testUser.getId());
        request.setRoomId(testRoom.getId());
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);

        assertThrows(IllegalStateException.class, () -> 
            bookingService.createBooking(request)
        );
    }

    @Test
    void cancelBooking_Success() {
        // Setup
        Long bookingId = 1L;
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUser(testUser);
        booking.setRoom(testRoom);
        booking.setCheckInDate(LocalDate.now().plusDays(1));
        booking.setCheckOutDate(LocalDate.now().plusDays(3));
        booking.setStatus(Booking.BookingStatus.CONFIRMED);

        when(cacheService.getBooking(bookingId)).thenReturn(null); // force DB lookup
        when(bookingDao.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingDao.save(any())).thenReturn(booking);

        // Execute
        bookingService.cancelBooking(bookingId);

        // Verify
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void getBooking_NotFound() {
        // Setup
        when(bookingDao.findById(999L)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () -> 
            bookingService.getBooking(999L)
        );
    }
}
