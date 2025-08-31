package com.example.hotelbooking.db;

import com.zaxxer.hikari.HikariDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseInitializer {
    private final HikariDataSource dataSource;

    public DatabaseInitializer(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initialize() {
        createTables();
        insertTestData();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection()) {
            // Create tables
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    name VARCHAR(255) NOT NULL," +
                "    email VARCHAR(255) NOT NULL UNIQUE," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS hotels (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    name VARCHAR(255) NOT NULL," +
                "    address VARCHAR(255) NOT NULL," +
                "    rating INT NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    hotel_id BIGINT NOT NULL," +
                "    room_number VARCHAR(50) NOT NULL," +
                "    room_type VARCHAR(50) NOT NULL," +
                "    price_per_night DECIMAL(10,2) NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (hotel_id) REFERENCES hotels(id)" +
                ")");

            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS bookings (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    user_id BIGINT NOT NULL," +
                "    room_id BIGINT NOT NULL," +
                "    check_in_date DATE NOT NULL," +
                "    check_out_date DATE NOT NULL," +
                "    total_price DECIMAL(10,2) NOT NULL," +
                "    status VARCHAR(20) NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (user_id) REFERENCES users(id)," +
                "    FOREIGN KEY (room_id) REFERENCES rooms(id)" +
                ")");

            // Create indexes
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_bookings_room_id ON bookings(room_id)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_bookings_dates ON bookings(check_in_date, check_out_date)");
            conn.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    private void insertTestData() {
        try (Connection conn = dataSource.getConnection()) {
            // Insert test users
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email) VALUES (?, ?)"
            )) {
                ps.setString(1, "John Doe");
                ps.setString(2, "john@example.com");
                ps.executeUpdate();

                ps.setString(1, "Jane Smith");
                ps.setString(2, "jane@example.com");

                ps.setString(1, "Jamie Smith");
                ps.setString(2, "jamie@example.com");
                ps.executeUpdate();
            }

            // Insert test hotels
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hotels (name, address, rating) VALUES (?, ?, ?)"
            )) {
                ps.setString(1, "Grand Hotel");
                ps.setString(2, "123 Main St");
                ps.setInt(3, 5);
                ps.executeUpdate();

                ps.setString(1, "Seaside Resort");
                ps.setString(2, "456 Beach Rd");
                ps.setInt(3, 4);
                ps.executeUpdate();
            }

            // Insert test rooms
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rooms (hotel_id, room_number, room_type, price_per_night) VALUES (?, ?, ?, ?)"
            )) {
                // Rooms for Grand Hotel
                ps.setLong(1, 1);
                ps.setString(2, "101");
                ps.setString(3, "DELUXE");
                ps.setBigDecimal(4, new BigDecimal("200.00"));
                ps.executeUpdate();

                ps.setString(2, "102");
                ps.setString(3, "SUITE");
                ps.setBigDecimal(4, new BigDecimal("350.00"));
                ps.executeUpdate();

                // Rooms for Seaside Resort
                ps.setLong(1, 2);
                ps.setString(2, "201");
                ps.setString(3, "OCEAN_VIEW");
                ps.setBigDecimal(4, new BigDecimal("300.00"));
                ps.executeUpdate();

                ps.setString(2, "202");
                ps.setString(3, "BEACH_FRONT");
                ps.setBigDecimal(4, new BigDecimal("450.00"));
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert test data", e);
        }
    }
}
