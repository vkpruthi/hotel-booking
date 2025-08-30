package com.example.hotelbooking.service;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.User;
import com.example.hotelbooking.repository.BookingRepository;
import com.example.hotelbooking.repository.HotelRepository;
import com.example.hotelbooking.repository.RoomRepository;
import com.example.hotelbooking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private User testUser;
    private Room testRoom;
    private Hotel testHotel;

    @Autowired
    private HotelRepository hotelRepository;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        userRepository.save(testUser);

        // Create and save test hotel first
        testHotel = new Hotel();
        testHotel.setName("Test Hotel");
        testHotel.setAddress("Test Address");
        testHotel.setRating(4);
        testHotel = hotelRepository.save(testHotel);  // Save hotel first and get the managed entity

        // Create and save test room
        testRoom = new Room();
        testRoom.setRoomNumber("101");
        testRoom.setRoomType("STANDARD");
        testRoom.setPricePerNight(new BigDecimal("100.00"));
        testRoom.setHotel(testHotel);  // Use the managed hotel entity
        testRoom = roomRepository.save(testRoom);
    }

    @Test
    void createBooking_Success() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        Booking booking = bookingService.createBooking(
                testUser.getId(),
                testRoom.getId(),
                checkIn,
                checkOut
        );

        assertNotNull(booking);
        assertEquals(testUser.getId(), booking.getUser().getId());
        assertEquals(testRoom.getId(), booking.getRoom().getId());
        assertEquals(checkIn, booking.getCheckInDate());
        assertEquals(checkOut, booking.getCheckOutDate());
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(new BigDecimal("200.00"), booking.getTotalPrice());
    }

    @Test
    void createBooking_RoomNotAvailable() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        // Create an existing booking for the same room and dates
        bookingService.createBooking(
                testUser.getId(),
                testRoom.getId(),
                checkIn,
                checkOut
        );

        // Try to create another booking for the same room and dates
        assertThrows(IllegalStateException.class, () -> {
            bookingService.createBooking(
                    testUser.getId(),
                    testRoom.getId(),
                    checkIn,
                    checkOut
            );
        });
    }

    @Test
    void cancelBooking_Success() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(3);

        Booking booking = bookingService.createBooking(
                testUser.getId(),
                testRoom.getId(),
                checkIn,
                checkOut
        );

        bookingService.cancelBooking(booking.getId());

        Booking cancelledBooking = bookingService.getBooking(booking.getId());
        assertEquals(Booking.BookingStatus.CANCELLED, cancelledBooking.getStatus());
    }

    @Test
    void getBooking_NotFound() {
        assertThrows(EntityNotFoundException.class, () -> {
            bookingService.getBooking(999L);
        });
    }
}
