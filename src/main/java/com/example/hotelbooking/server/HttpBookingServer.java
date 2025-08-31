package com.example.hotelbooking.server;

import com.example.hotelbooking.service.BookingService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpBookingServer {
    private static final int PORT = 8080;
    private static final int NUM_THREADS = 500;  // Increased thread pool size
    private static final int BACKLOG = 1000;  // Increased connection backlog
    
    private final HttpServer server;
    private final BookingService bookingService;
    private final ExecutorService executorService;
    
    public HttpBookingServer() throws IOException {
        this(new InetSocketAddress(PORT), NUM_THREADS, new BookingService(null));
    }

    public HttpBookingServer(BookingService bookingService) throws IOException {
        this(new InetSocketAddress(PORT), NUM_THREADS, bookingService);
    }
    
    public HttpBookingServer(InetSocketAddress address, int numThreads, BookingService bookingService) throws IOException {
        this.server = HttpServer.create(address, BACKLOG);
        this.bookingService = bookingService;
        
        // Create shared executor service
        this.executorService = Executors.newFixedThreadPool(numThreads, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true); // Use daemon threads to avoid blocking JVM shutdown
            return t;
        });
        
        // Create context for bookings endpoint
        this.server.createContext("/api/bookings", new BookingHandler(bookingService));
        
        // Use the shared executor service
        this.server.setExecutor(this.executorService);
    }
    
    public void start() {
        this.server.start();
    }
    
    public void stop() {
        this.server.stop(0);
        this.executorService.shutdownNow(); // Clean up thread pool
    }
}
