package com.example.hotelbooking.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Custom metrics registry for tracking application metrics
 * Optimized for high concurrency using LongAdder
 */
public class MetricsRegistry {
    private static final MetricsRegistry INSTANCE = new MetricsRegistry();
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    private MetricsRegistry() {}

    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public long getCounterValue(String name) {
        return counters.getOrDefault(name, new LongAdder()).sum();
    }

    public Map<String, Long> getAllMetrics() {
        Map<String, Long> metrics = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> metrics.put(key, value.sum()));
        return metrics;
    }
}
