package com.example.hotelbooking.server;

import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.metrics.MetricsRegistry;
import com.example.hotelbooking.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    // Rate limiter: 5000 permits to handle high load
    private final Semaphore rateLimiter = new Semaphore(1000, true);  // Fair semaphore with reduced permits
    private static final Pattern BOOKING_ID_PATTERN = Pattern.compile("/api/bookings/(\\d+)");
    private static final Pattern USER_BOOKINGS_PATTERN = Pattern.compile("/api/bookings/user/(\\d+)");

    public BookingHandler(BookingService bookingService) {
        this.bookingService = bookingService;
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper for better date/time handling and error messages
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.findAndRegisterModules(); // Register all available modules
        this.metricsRegistry = MetricsRegistry.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        boolean permitAcquired = false;
        try {
            // Try to acquire a permit with timeout
            permitAcquired = rateLimiter.tryAcquire(2, TimeUnit.SECONDS);
            if (!permitAcquired) {
                sendError(exchange, 429, "Too Many Requests");
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
                        sendError(exchange, 405, "Method Not Allowed");
                }
            } catch (IOException e) {
                sendError(exchange, 500, "IO Error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Bad Request: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(exchange, 503, "Service Unavailable");
        } finally {
            if (permitAcquired) {
                rateLimiter.release();
            }
            exchange.close();
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
                .collect(java.util.stream.Collectors.toList());
        sendResponse(exchange, 200, response);
    }

    private void handleGetBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.get.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        var booking = bookingService.getBooking(bookingId);
        sendResponse(exchange, 200, convertToResponse(booking));
    }

    private <T> T readRequest(HttpExchange exchange, Class<T> clazz) throws IOException {
        // Read the entire request body into a byte array first
        byte[] requestBody;
        try (InputStream is = exchange.getRequestBody()) {
            requestBody = is.readAllBytes();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Failed to read request body: " + e.getMessage());
        }
        
        // Then parse the byte array
        try {
            return objectMapper.readValue(requestBody, clazz);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Invalid request body: " + e.getMessage());
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        byte[] responseBytes;
        if (response instanceof String && ((String) response).isEmpty()) {
            responseBytes = new byte[0];
        } else {
            try {
                responseBytes = objectMapper.writeValueAsBytes(response);
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Error serializing response: " + e.getMessage());
                return;
            }
        }

        synchronized (exchange) {
            try {
                if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                }
                if (responseBytes.length == 0) {
                    exchange.sendResponseHeaders(statusCode, -1);
                } else {
                    exchange.sendResponseHeaders(statusCode, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                        os.flush();
                    }
                }
            } catch (IOException e) {
                // If we get here with "headers already sent", just log it and continue
                if (e.getMessage() != null && e.getMessage().contains("headers already sent")) {
                    System.err.println("Warning: Headers already sent for exchange: " + e.getMessage());
                } else {
                    throw e;
                }
            }
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
