package com.example.hotelbooking.server;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private static final int CONCURRENT_REQUESTS = 100;
    private static final int REQUESTS_PER_THREAD = 30;
    private HttpBookingServer server;
    private ObjectMapper objectMapper;
    
    @Mock
    private BookingService bookingService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        server = new HttpBookingServer();
        server.start();

        // Mock booking service response
        Booking mockBooking = new Booking();
        when(bookingService.createBooking(any())).thenReturn(mockBooking);
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
                        makeBookingRequest();
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }
            });
        }

        // Shutdown executor and wait for completion
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Assert results
        int totalRequests = CONCURRENT_REQUESTS * REQUESTS_PER_THREAD;
        int totalResponses = successCount.get() + errorCount.get();
        
        assertEquals(totalRequests, totalResponses, "All requests should be processed");
        assertTrue(errorCount.get() < totalRequests * 0.1, "Error rate should be less than 10%");
    }

    private void makeBookingRequest() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:8080/api/bookings").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

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
        assertTrue(responseCode == 200 || responseCode == 429, "Response code should be either 200 or 429");
    }
}
