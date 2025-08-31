package com.example.hotelbooking.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


public class DatabaseConfig {
    private final HikariDataSource dataSource;

    public DatabaseConfig() {
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:hoteldb;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        // Configure for 3M requests/hour (833 req/sec)
        // Assuming avg DB operation takes 50ms, we need ~42 connections to handle 833 req/sec
        // Add some buffer for peaks
        config.setMaximumPoolSize(100);
        config.setMinimumIdle(50);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(2000); // Faster timeout for high throughput
        config.setMaxLifetime(1800000);    // 30 minutes max connection lifetime
        config.setKeepaliveTime(60000);    // 1 minute keepalive
        config.setAutoCommit(true);
        
        // Add validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(1000);
        
        // Add performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
