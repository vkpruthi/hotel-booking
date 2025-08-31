package com.example.hotelbooking.dao;

import com.example.hotelbooking.model.Booking;
import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.User;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookingDao {
    private final DataSource dataSource;

    public BookingDao(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create users table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    name VARCHAR(255) NOT NULL," +
                "    email VARCHAR(255) NOT NULL UNIQUE," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Create hotels table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS hotels (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    name VARCHAR(255) NOT NULL," +
                "    address VARCHAR(500)," +
                "    rating INT," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            // Create rooms table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    room_number VARCHAR(50) NOT NULL UNIQUE," +
                "    room_type VARCHAR(50) NOT NULL," +
                "    price_per_night DECIMAL(10,2) NOT NULL," +
                "    hotel_id BIGINT," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (hotel_id) REFERENCES hotels(id)" +
                ")");

            // Create bookings table
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
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (user_id) REFERENCES users(id)," +
                "    FOREIGN KEY (room_id) REFERENCES rooms(id)" +
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
        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection();
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
        // First check if there's any conflicting booking
        if (!isRoomAvailable(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate())) {
            throw new RuntimeException("Room is already booked for these dates");
        }

        String sql = booking.getId() == null ?
            "INSERT INTO bookings (user_id, room_id, check_in_date, check_out_date, total_price, status) VALUES (?, ?, ?, ?, ?, ?)" :
            "UPDATE bookings SET user_id=?, room_id=?, check_in_date=?, check_out_date=?, total_price=?, status=? WHERE id=?";

        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
            
            ps.setLong(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete booking with id: " + id, e);
        }
    }

    public List<Booking> findByUserId(Long userId) {
        List<Booking> bookings = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection();
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
        
        // Set up User and Room with full details
        booking.setUser(findUserById(rs.getLong("user_id")).orElse(null));
        booking.setRoom(findRoomById(rs.getLong("room_id")).orElse(null));
        
        booking.setCheckInDate(rs.getDate("check_in_date").toLocalDate());
        booking.setCheckOutDate(rs.getDate("check_out_date").toLocalDate());
        booking.setTotalPrice(rs.getBigDecimal("total_price"));
        booking.setStatus(Booking.BookingStatus.valueOf(rs.getString("status")));
        
        return booking;
    }

    public Optional<User> findUserById(Long userId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                return Optional.of(user);
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id: " + userId, e);
        }
    }

    private Optional<Hotel> findHotelById(Long hotelId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM hotels WHERE id = ?")) {
            
            ps.setLong(1, hotelId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Hotel hotel = new Hotel();
                hotel.setId(rs.getLong("id"));
                hotel.setName(rs.getString("name"));
                hotel.setAddress(rs.getString("address"));
                hotel.setRating(rs.getInt("rating"));
                return Optional.of(hotel);
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find hotel by id: " + hotelId, e);
        }
    }

    public Optional<Room> findRoomById(Long roomId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT r.*, h.id as hotel_id FROM rooms r LEFT JOIN hotels h ON r.hotel_id = h.id WHERE r.id = ?")) {
            
            ps.setLong(1, roomId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Room room = new Room();
                room.setId(rs.getLong("id"));
                room.setRoomNumber(rs.getString("room_number"));
                room.setRoomType(rs.getString("room_type"));
                room.setPricePerNight(rs.getBigDecimal("price_per_night"));
                
                Long hotelId = rs.getLong("hotel_id");
                if (!rs.wasNull()) {
                    findHotelById(hotelId).ifPresent(room::setHotel);
                }
                
                return Optional.of(room);
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find room by id: " + roomId, e);
        }
    }
}
