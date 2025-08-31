package com.example.hotelbooking.http.handlers;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.metrics.MetricsRegistry;
import com.example.hotelbooking.dto.BookingRequest;
import com.example.hotelbooking.dto.BookingResponse;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class BookingHandler implements HttpHandler {
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;
    // Rate limiter: 833 requests per second (3M per hour)
    // Configure for 3M requests/hour = ~833 req/sec
    private final Semaphore rateLimiter = new Semaphore(833);
    
    // Request queue for handling bursts
    private final ArrayBlockingQueue<Runnable> requestQueue = new ArrayBlockingQueue<>(10000);
    
    private static final Pattern BOOKING_ID_PATTERN = Pattern.compile("/api/bookings/(\\d+)");
    private static final Pattern USER_BOOKINGS_PATTERN = Pattern.compile("/api/bookings/user/(\\d+)");
    
    // Timeouts
    private static final long REQUEST_TIMEOUT_MS = 2000; // 2 seconds
    private static final long QUEUE_TIMEOUT_MS = 1000;   // 1 second

    public BookingHandler(BookingService bookingService) {
        this.bookingService = bookingService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())  // Better date handling
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // More resilient parsing
        this.metricsRegistry = MetricsRegistry.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Runnable task = () -> {
            try {
                processRequest(exchange);
            } catch (Exception e) {
                try {
                    sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
                } catch (IOException ioe) {
                    // Log error
                    System.err.println("Failed to send error response: " + ioe.getMessage());
                }
            }
        };

        try {
            // Try to queue the request
            if (!requestQueue.offer(task, QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                sendResponse(exchange, 503, "Service Temporarily Unavailable");
                return;
            }

            // Try to acquire rate limit permit
            if (!rateLimiter.tryAcquire(1, REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                sendResponse(exchange, 429, "Too Many Requests");
                return;
            }

            try {
                task.run();
            } finally {
                rateLimiter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendResponse(exchange, 503, "Service Interrupted");
        }
    }

    private void processRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        metricsRegistry.incrementCounter("http.requests.total");
        metricsRegistry.incrementCounter("http.requests." + method.toLowerCase());

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
            metricsRegistry.incrementCounter("http.requests.success");
        } catch (Exception e) {
            metricsRegistry.incrementCounter("http.requests.error");
            throw e;
        }
    }
    private void handleCreateBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.create.requests");
        BookingRequest request = readRequest(exchange, BookingRequest.class);
        Booking booking = bookingService.createBooking(request);
        sendResponse(exchange, 200, convertToResponse(booking));
    }

    private void handleUpdateBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.update.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        BookingRequest request = readRequest(exchange, BookingRequest.class);
        Booking booking = bookingService.updateBooking(bookingId, request.getCheckInDate(), request.getCheckOutDate());
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
        List<Booking> bookings = bookingService.getUserBookings(userId);
        List<BookingResponse> response = bookings.stream()
                .map(this::convertToResponse)
                .collect(java.util.stream.Collectors.toList());
        sendResponse(exchange, 200, response);
    }

    private void handleGetBooking(HttpExchange exchange) throws IOException {
        metricsRegistry.incrementCounter("booking.get.requests");
        Long bookingId = extractId(exchange.getRequestURI().getPath(), BOOKING_ID_PATTERN);
        Booking booking = bookingService.getBooking(bookingId);
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
