package com.example.hotelbooking.server;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.metrics.MetricsRegistry;
import com.example.hotelbooking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Handler for booking-related HTTP requests
 * Implements rate limiting and request throttling for high-load scenarios
 */
public class BookingHandler implements HttpHandler {
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;
    // Rate limiter: 833 requests per second (3M per hour)
    private final Semaphore rateLimiter = new Semaphore(833);
    private static final Pattern BOOKING_ID_PATTERN = Pattern.compile("/api/bookings/(\\d+)");
    private static final Pattern USER_BOOKINGS_PATTERN = Pattern.compile("/api/bookings/user/(\\d+)");

    public BookingHandler(BookingService bookingService) {
        this.bookingService = bookingService;
        this.objectMapper = new ObjectMapper();
        this.metricsRegistry = MetricsRegistry.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Try to acquire a permit with timeout
            if (!rateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
                sendResponse(exchange, 429, "Too Many Requests");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                switch (method) {
                    case "POST":
                        handleCreateBooking(exchange);
                        break;
                    case "PUT":
                        handleUpdateBooking(exchange);
                        break;
                    case "DELETE":
                        handleCancelBooking(exchange);
                        break;
                    case "GET":
                        if (path.contains("/user/")) {
                            handleGetUserBookings(exchange);
                        } else {
                            handleGetBooking(exchange);
                        }
                        break;
                    default:
                        sendResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        } finally {
            rateLimiter.release();
        }
    }

    private void handleCreateBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.create.requests");
        BookingRequest request = readRequest(exchange, BookingRequest.class);
        var booking = bookingService.createBooking(request);
        sendResponse(exchange, 200, convertToResponse(booking));
    }

    private void handleUpdateBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.update.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        BookingRequest request = readRequest(exchange, BookingRequest.class);
        var booking = bookingService.updateBooking(bookingId, request.getCheckInDate(), request.getCheckOutDate());
        sendResponse(exchange, 200, convertToResponse(booking));
    }

    private void handleCancelBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.cancel.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        bookingService.cancelBooking(bookingId);
        sendResponse(exchange, 200, "");
    }

    private void handleGetUserBookings(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.user.requests");
        Long userId = extractId(exchange.getRequestURI().getPath(), USER_BOOKINGS_PATTERN);
        var bookings = bookingService.getUserBookings(userId);
        var response = bookings.stream()
                .map(this::convertToResponse)
                .toList();
        sendResponse(exchange, 200, response);
    }

    private void handleGetBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.get.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        var booking = bookingService.getBooking(bookingId);
        sendResponse(exchange, 200, convertToResponse(booking));
    }

    private <T> T readRequest(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return objectMapper.readValue(is, clazz);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        byte[] responseBytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private Long extractId(String path, Pattern pattern) {
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new IllegalArgumentException("Invalid path: " + path);
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
