package com.example.hotelbooking;

import com.example.hotelbooking.db.DatabaseConfig;
import com.example.hotelbooking.db.DatabaseInitializer;
import com.example.hotelbooking.http.Router;
import com.example.hotelbooking.service.BookingService;
import com.example.hotelbooking.dao.BookingDao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class HotelBookingApplication {
    private static final Logger logger = Logger.getLogger(HotelBookingApplication.class.getName());

    public static void main(String[] args) {
        try {
            // Load configuration
            Properties config = loadConfig();
            
            // Initialize database
            DatabaseConfig dbConfig = new DatabaseConfig();
            DatabaseInitializer dbInitializer = new DatabaseInitializer(dbConfig.getDataSource());
            dbInitializer.initialize();
            
            // Initialize components
            BookingDao bookingDao = new BookingDao(dbConfig.getDataSource());
            BookingService bookingService = new BookingService(bookingDao);
            
            // Start HTTP server
            Router router = new Router(bookingService);
            router.start();
            
            logger.info("Hotel Booking Application started successfully on port " + config.getProperty("server.port"));
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down application...");
                router.stop();
                dbConfig.shutdown();
                logger.info("Application shutdown complete.");
            }));
            
        } catch (Exception e) {
            logger.severe("Failed to start application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = HotelBookingApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            props.load(is);
        }
        return props;
    }
}