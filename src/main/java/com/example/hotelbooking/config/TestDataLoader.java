package com.example.hotelbooking.config;

import com.example.hotelbooking.model.Hotel;
import com.example.hotelbooking.model.Room;
import com.example.hotelbooking.model.User;
import com.example.hotelbooking.repository.HotelRepository;
import com.example.hotelbooking.repository.RoomRepository;
import com.example.hotelbooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class TestDataLoader {

    @Bean
    CommandLineRunner initDatabase(
            @Autowired HotelRepository hotelRepository,
            @Autowired RoomRepository roomRepository,
            @Autowired UserRepository userRepository) {
        return args -> {
            // Create test users
            User user1 = new User();
            user1.setName("John Doe");
            user1.setEmail("john@example.com");
            userRepository.save(user1);

            User user2 = new User();
            user2.setName("Jane Smith");
            user2.setEmail("jane@example.com");
            userRepository.save(user2);

            // Create test hotels
            Hotel hotel1 = new Hotel();
            hotel1.setName("Grand Hotel");
            hotel1.setAddress("123 Main St");
            hotel1.setRating(5);
            hotelRepository.save(hotel1);

            Hotel hotel2 = new Hotel();
            hotel2.setName("Seaside Resort");
            hotel2.setAddress("456 Beach Rd");
            hotel2.setRating(4);
            hotelRepository.save(hotel2);

            // Create test rooms
            Room room1 = new Room();
            room1.setRoomNumber("101");
            room1.setRoomType("DELUXE");
            room1.setPricePerNight(new BigDecimal("200.00"));
            room1.setHotel(hotel1);
            roomRepository.save(room1);

            Room room2 = new Room();
            room2.setRoomNumber("102");
            room2.setRoomType("SUITE");
            room2.setPricePerNight(new BigDecimal("350.00"));
            room2.setHotel(hotel1);
            roomRepository.save(room2);

            Room room3 = new Room();
            room3.setRoomNumber("201");
            room3.setRoomType("OCEAN_VIEW");
            room3.setPricePerNight(new BigDecimal("300.00"));
            room3.setHotel(hotel2);
            roomRepository.save(room3);

            Room room4 = new Room();
            room4.setRoomNumber("202");
            room4.setRoomType("BEACH_FRONT");
            room4.setPricePerNight(new BigDecimal("450.00"));
            room4.setHotel(hotel2);
            roomRepository.save(room4);
        };
    }
}
