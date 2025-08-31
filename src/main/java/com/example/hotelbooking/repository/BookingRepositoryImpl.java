package com.example.hotelbooking.repository;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.db.ConnectionPool;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookingRepositoryImpl {
    private final ConnectionPool connectionPool;

    public BookingRepositoryImpl() {
        this.connectionPool = new ConnectionPool(
            "jdbc:h2:mem:hoteldb;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create tables
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS bookings (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    user_id BIGINT NOT NULL," +
                "    room_id BIGINT NOT NULL," +
                "    check_in_date DATE NOT NULL," +
                "    check_out_date DATE NOT NULL," +
                "    total_price DECIMAL(10,2) NOT NULL," +
                "    status VARCHAR(20) NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Create indexes for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bookings_room_id ON bookings(room_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bookings_dates ON bookings(check_in_date, check_out_date)");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public Optional<Booking> findById(Long id) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM bookings WHERE id = ?")) {
            
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapToBooking(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find booking by id: " + id, e);
        }
    }

    public List<Booking> findAll() {
        List<Booking> bookings = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM bookings")) {

            while (rs.next()) {
                bookings.add(mapToBooking(rs));
            }
            return bookings;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all bookings", e);
        }
    }

    public Booking save(Booking booking) {
        String sql = booking.getId() == null ?
                "INSERT INTO bookings (user_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)" :
                "UPDATE bookings SET user_id=?, room_id=?, check_in_date=?, check_out_date=?, total_price=?, status=? WHERE id=?";

        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, booking.getUser().getId());
            ps.setLong(2, booking.getRoom().getId());
            ps.setDate(3, Date.valueOf(booking.getCheckInDate()));
            ps.setDate(4, Date.valueOf(booking.getCheckOutDate()));
            ps.setBigDecimal(5, booking.getTotalPrice());
            ps.setString(6, booking.getStatus().name());

            if (booking.getId() != null) {
                ps.setLong(7, booking.getId());
            }

            ps.executeUpdate();

            if (booking.getId() == null) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    booking.setId(rs.getLong(1));
                }
            }

            return booking;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save booking", e);
        }
    }

    public void deleteById(Long id) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
            
            ps.setLong(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete booking with id: " + id, e);
        }
    }

    public List<Booking> findByUserId(Long userId) {
        List<Booking> bookings = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM bookings WHERE user_id = ?")) {
            
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                bookings.add(mapToBooking(rs));
            }
            return bookings;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find bookings for user: " + userId, e);
        }
    }

    public boolean isRoomAvailable(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM bookings " +
                "WHERE room_id = ? " +
                "AND status != 'CANCELLED' " +
                "AND (" +
                "    (check_in_date <= ? AND check_out_date > ?) OR " +
                "    (check_in_date < ? AND check_out_date >= ?) OR " +
                "    (check_in_date >= ? AND check_out_date <= ?)" +
                ")")) {
            
            ps.setLong(1, roomId);
            ps.setDate(2, Date.valueOf(checkOut));
            ps.setDate(3, Date.valueOf(checkIn));
            ps.setDate(4, Date.valueOf(checkOut));
            ps.setDate(5, Date.valueOf(checkOut));
            ps.setDate(6, Date.valueOf(checkIn));
            ps.setDate(7, Date.valueOf(checkOut));

            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) == 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check room availability", e);
        }
    }

    private Booking mapToBooking(ResultSet rs) throws SQLException {
        Booking booking = new Booking();
        booking.setId(rs.getLong("id"));
        // Note: We need to fetch user and room details separately
        // In a real implementation, we would need to join with users and rooms tables
        return booking;
    }
}
