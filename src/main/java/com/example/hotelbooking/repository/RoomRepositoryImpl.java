package com.example.hotelbooking.repository;

import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.db.ConnectionPool;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoomRepositoryImpl {
    private final ConnectionPool connectionPool;

    public RoomRepositoryImpl() {
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
            
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "    hotel_id BIGINT NOT NULL," +
                "    room_number VARCHAR(10) NOT NULL," +
                "    room_type VARCHAR(50) NOT NULL," +
                "    price_per_night DECIMAL(10,2) NOT NULL," +
                "    max_occupancy INT NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rooms_hotel_id ON rooms(hotel_id)");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    @Override
    public Optional<Room> findById(Long id) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM rooms WHERE id = ?")) {
            
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapToRoom(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find room by id: " + id, e);
        }
    }

    @Override
    public List<Room> findAll() {
        List<Room> rooms = new ArrayList<>();
        try (Connection conn = connectionPool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM rooms")) {

            while (rs.next()) {
                rooms.add(mapToRoom(rs));
            }
            return rooms;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all rooms", e);
        }
    }

    @Override
    public Room save(Room room) {
        String sql = room.getId() == null ?
                "INSERT INTO rooms (hotel_id, room_number, room_type, price_per_night, max_occupancy) VALUES (?, ?, ?, ?, ?)" :
                "UPDATE rooms SET hotel_id=?, room_number=?, room_type=?, price_per_night=?, max_occupancy=? WHERE id=?";

        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, room.getHotel().getId());
            ps.setString(2, room.getRoomNumber());
            ps.setString(3, room.getRoomType());
            ps.setBigDecimal(4, room.getPricePerNight());
            ps.setInt(5, room.getMaxOccupancy());

            if (room.getId() != null) {
                ps.setLong(6, room.getId());
            }

            ps.executeUpdate();

            if (room.getId() == null) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    room.setId(rs.getLong(1));
                }
            }

            return room;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save room", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM rooms WHERE id = ?")) {
            
            ps.setLong(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete room with id: " + id, e);
        }
    }

    public List<Room> findAvailableRooms(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT r.* FROM rooms r " +
                "WHERE r.hotel_id = ? " +
                "AND r.id NOT IN (" +
                "    SELECT b.room_id FROM bookings b " +
                "    WHERE b.status = 'CONFIRMED' " +
                "    AND (" +
                "        (b.check_in_date BETWEEN ? AND ?) OR " +
                "        (b.check_out_date BETWEEN ? AND ?) OR " +
                "        (b.check_in_date <= ? AND b.check_out_date >= ?)" +
                "    )" +
                ")")) {
            
            ps.setLong(1, hotelId);
            ps.setDate(2, Date.valueOf(checkIn));
            ps.setDate(3, Date.valueOf(checkOut));
            ps.setDate(4, Date.valueOf(checkIn));
            ps.setDate(5, Date.valueOf(checkOut));
            ps.setDate(6, Date.valueOf(checkIn));
            ps.setDate(7, Date.valueOf(checkOut));

            ResultSet rs = ps.executeQuery();
            List<Room> rooms = new ArrayList<>();
            while (rs.next()) {
                rooms.add(mapToRoom(rs));
            }
            return rooms;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find available rooms", e);
        }
    }

    private Room mapToRoom(ResultSet rs) throws SQLException {
        Room room = new Room();
        room.setId(rs.getLong("id"));
        room.setRoomNumber(rs.getString("room_number"));
        room.setRoomType(rs.getString("room_type"));
        room.setPricePerNight(rs.getBigDecimal("price_per_night"));
        room.setMaxOccupancy(rs.getInt("max_occupancy"));
        
        // Set up Hotel
        Hotel hotel = new Hotel();
        hotel.setId(rs.getLong("hotel_id"));
        room.setHotel(hotel);
        
        return room;
    }
}
