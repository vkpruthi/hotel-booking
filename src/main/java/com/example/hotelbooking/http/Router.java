package com.example.hotelbooking.http;

import com.example.hotelbooking.http.handlers.BookingHandler;
import com.example.hotelbooking.http.handlers.MetricsHandler;
import com.example.hotelbooking.service.BookingService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Router {
    private final HttpServer server;
    private static final int PORT = 8080;
    // Calculate optimal thread pool size:
    // Threads = CPU cores * (1 + avg wait time / avg service time)
    // Assuming 80% IO wait time (DB, network) and 20% CPU time
    // Formula: Cores * (1 + 0.8/0.2) = Cores * 5
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 5;
    
    // Socket backlog size to handle connection bursts
    private static final int BACKLOG = 10000;

    public Router(BookingService bookingService) throws Exception {
        // Configure server with connection backlog
        this.server = HttpServer.create(new InetSocketAddress(PORT), BACKLOG);
        
        // Use cached thread pool with bounded queue
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            THREAD_POOL_SIZE, // core pool size
            THREAD_POOL_SIZE * 2, // max pool size
            60L, TimeUnit.SECONDS, // thread keep alive time
            new ArrayBlockingQueue<>(10000), // bounded queue for backpressure
            new ThreadPoolExecutor.CallerRunsPolicy() // handle queue overflow
        );
        
        server.setExecutor(executor);
        
        // Register handlers
        server.createContext("/api/bookings", new BookingHandler(bookingService));
        server.createContext("/metrics", new MetricsHandler());
    }

    public void start() {
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    public void stop() {
        server.stop(0);
    }
}
