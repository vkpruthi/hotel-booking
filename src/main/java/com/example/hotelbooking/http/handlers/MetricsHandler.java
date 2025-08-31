package com.example.hotelbooking.http.handlers;

import com.example.hotelbooking.metrics.MetricsRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;

public class MetricsHandler implements HttpHandler {
    private final ObjectMapper objectMapper;
    private final MetricsRegistry metricsRegistry;

    public MetricsHandler() {
        this.objectMapper = new ObjectMapper();
        this.metricsRegistry = MetricsRegistry.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            var metrics = metricsRegistry.getAllMetrics();
            sendResponse(exchange, 200, metrics);
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        byte[] responseBytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
