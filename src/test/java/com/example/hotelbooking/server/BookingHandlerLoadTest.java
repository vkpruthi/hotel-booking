package com.example.hotelbooking.server;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.User;
import com.example.hotelbooking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class BookingHandlerLoadTest {
    private static final int CONCURRENT_REQUESTS = 40;  // concurrent request for stability
    private static final int REQUESTS_PER_THREAD = 25;  // Slightly increased per-thread requests
    private HttpBookingServer server;
    private ObjectMapper objectMapper;
    
    @Mock
    private BookingService bookingService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper().findAndRegisterModules(); // Add support for LocalDate

        // Create a complete mock booking response
        Booking mockBooking = new Booking();
        mockBooking.setId(1L);

        // Setup mock user
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setName("Test User");
        mockBooking.setUser(mockUser);

        // Setup mock hotel and room
        Hotel mockHotel = new Hotel();
        mockHotel.setId(1L);
        mockHotel.setName("Test Hotel");

        Room mockRoom = new Room();
        mockRoom.setId(1L);
        mockRoom.setRoomNumber("101");
        mockRoom.setHotel(mockHotel);
        mockBooking.setRoom(mockRoom);

        // Set other booking details
        mockBooking.setCheckInDate(LocalDate.now());
        mockBooking.setCheckOutDate(LocalDate.now().plusDays(1));
        mockBooking.setTotalPrice(new BigDecimal("100.00"));
        mockBooking.setStatus(Booking.BookingStatus.CONFIRMED);

        // Mock booking service response
        when(bookingService.createBooking(any())).thenReturn(mockBooking);

        server = new HttpBookingServer(bookingService); // Pass the mocked service
        server.start();
    }

    @Test
    void handleHighLoad() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Create tasks for concurrent requests
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                    try {
                        int responseCode = makeBookingRequest();
                        if (responseCode == 200) {
                            successCount.incrementAndGet();
                        } else {
                            System.out.println("Request failed with code: " + responseCode);
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.out.println("Request error: " + e.getMessage());
                        errorCount.incrementAndGet();
                    }
                }
            });
        }

        // Wait for completion with logging
        executor.shutdown();
        boolean completed = executor.awaitTermination(2, TimeUnit.MINUTES);  // Increased timeout
        if (!completed) {
            System.out.println("Executor timed out before all tasks completed");
        }

        // Assert results with detailed output
        int totalRequests = CONCURRENT_REQUESTS * REQUESTS_PER_THREAD;
        int totalResponses = successCount.get() + errorCount.get();
        int successRate = (successCount.get() * 100) / totalRequests;
        
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Total responses: " + totalResponses);
        System.out.println("Successful requests: " + successCount.get() + " (" + successRate + "%)");
        System.out.println("Failed requests: " + errorCount.get() + " (" + ((errorCount.get() * 100) / totalRequests) + "%)");
        
        assertEquals(totalRequests, totalResponses, "All requests should be processed");
        assertTrue(errorCount.get() < totalRequests * 0.1, "Error rate should be less than 10%");
    }

    private int makeBookingRequest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:8080/api/bookings").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);  // Increased timeout
        connection.setReadTimeout(5000);   // Increased timeout

        BookingRequest request = new BookingRequest();
        request.setUserId(1L);
        request.setRoomId(1L);
        request.setCheckInDate(LocalDate.now());
        request.setCheckOutDate(LocalDate.now().plusDays(1));

        String jsonRequest = objectMapper.writeValueAsString(request);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonRequest.getBytes());
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200 && responseCode != 429) {
            System.out.println("Unexpected response code: " + responseCode);
            // Read error response if available
            try (InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    String errorResponse = new String(errorStream.readAllBytes());
                    System.out.println("Error response: " + errorResponse);
                }
            }
        }
        return responseCode;
    }
}
