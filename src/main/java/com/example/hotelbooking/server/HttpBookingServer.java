package com.example.hotelbooking.server;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Custom HTTP server implementation without using any framework
 * Optimized for handling 3M requests/hour (approximately 833 req/sec)
 */
public class HttpBookingServer {
    private final HttpServer server;
    private static final int PORT = 8080;
    // Calculate optimal thread pool size based on CPU cores and expected I/O ratio
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 8;

    public HttpBookingServer() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        // Configure a large backlog queue for incoming connections
        server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
    }

    public void start() {
        server.createContext("/api/bookings", new BookingHandler());
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    public void stop() {
        server.stop(0);
    }
}
