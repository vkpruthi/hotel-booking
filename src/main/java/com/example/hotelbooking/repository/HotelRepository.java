package com.example.hotelbooking.repository;

import com.example.hotelbooking.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
    // Custom queries can be added here if needed
}
