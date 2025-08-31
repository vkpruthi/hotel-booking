package com.example.hotelbooking.service;

import com.example.hotelbooking.model.Booking;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.hotelbooking.metrics.MetricsRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CacheService {
    private final Cache<Long, Booking> bookingCache;
    private final Cache<Long, List<Booking>> userBookingsCache;
    private final Cache<String, Boolean> roomAvailabilityCache;
    private final MetricsRegistry metricsRegistry;
    
    // Cache stats
    private final ConcurrentMap<String, Long> cacheHits = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> cacheMisses = new ConcurrentHashMap<>();

    public CacheService() {
        this.metricsRegistry = MetricsRegistry.getInstance();
        
        // Cache for individual bookings
        // Short TTL as bookings can be updated/cancelled
        this.bookingCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();

        // Cache for user bookings
        // Medium TTL as list changes less frequently
        this.userBookingsCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .recordStats()
                .build();

        // Cache for room availability
        // Very short TTL as availability changes frequently
        this.roomAvailabilityCache = Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofMinutes(1))
                .recordStats()
                .build();
    }

    // Booking cache methods
    public Booking getBooking(Long id) {
        return getWithStats("booking", () -> bookingCache.getIfPresent(id));
    }

    public void putBooking(Long id, Booking booking) {
        bookingCache.put(id, booking);
        metricsRegistry.incrementCounter("cache.booking.puts");
    }

    public void invalidateBooking(Long id) {
        bookingCache.invalidate(id);
        metricsRegistry.incrementCounter("cache.booking.invalidations");
    }

    // User bookings cache methods
    public List<Booking> getUserBookings(Long userId) {
        return getWithStats("user_bookings", () -> userBookingsCache.getIfPresent(userId));
    }

    public void putUserBookings(Long userId, List<Booking> bookings) {
        userBookingsCache.put(userId, bookings);
        metricsRegistry.incrementCounter("cache.user_bookings.puts");
    }

    public void invalidateUserBookings(Long userId) {
        userBookingsCache.invalidate(userId);
        metricsRegistry.incrementCounter("cache.user_bookings.invalidations");
    }

    // Room availability cache methods
    public Boolean getRoomAvailability(String key) {
        return getWithStats("room_availability", () -> roomAvailabilityCache.getIfPresent(key));
    }

    public void putRoomAvailability(String key, Boolean available) {
        roomAvailabilityCache.put(key, available);
        metricsRegistry.incrementCounter("cache.room_availability.puts");
    }

    public void invalidateRoomAvailability(String key) {
        roomAvailabilityCache.invalidate(key);
        metricsRegistry.incrementCounter("cache.room_availability.invalidations");
    }

    private <T> T getWithStats(String cacheType, Supplier<T> getter) {
        T value = getter.get();
        if (value != null) {
            cacheHits.merge(cacheType, 1L, Long::sum);
            metricsRegistry.incrementCounter("cache." + cacheType + ".hits");
        } else {
            cacheMisses.merge(cacheType, 1L, Long::sum);
            metricsRegistry.incrementCounter("cache." + cacheType + ".misses");
        }
        return value;
    }

    public void clearAll() {
        bookingCache.invalidateAll();
        userBookingsCache.invalidateAll();
        roomAvailabilityCache.invalidateAll();
        metricsRegistry.incrementCounter("cache.clear.all");
    }

    public Map<String, Double> getCacheHitRates() {
        Map<String, Double> hitRates = new HashMap<>();
        for (String cacheType : cacheHits.keySet()) {
            long hits = cacheHits.getOrDefault(cacheType, 0L);
            long misses = cacheMisses.getOrDefault(cacheType, 0L);
            double hitRate = hits + misses == 0 ? 0.0 : (double) hits / (hits + misses);
            hitRates.put(cacheType, hitRate);
        }
        return hitRates;
    }
}
