package com.acadify;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

public class DatabaseConfig {

    private static final int POOL_SIZE = 10;
    private static final Deque<Connection> pool = new ArrayDeque<>();

    // ✅ HARD-CODED CONFIG (for now)
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/acadify";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "postgres123";

    public static void initialize() {
        try {
            Class.forName("org.postgresql.Driver");

            for (int i = 0; i < POOL_SIZE; i++) {
                pool.push(createConnection());
            }

            System.out.println("[DatabaseConfig] Connection pool initialized successfully.");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("[DatabaseConfig] PostgreSQL JDBC driver not found.", e);
        } catch (SQLException e) {
            throw new RuntimeException("[DatabaseConfig] Failed to initialize connection pool.", e);
        }
    }

    public static synchronized Connection getConnection() throws SQLException {
        Connection conn = pool.poll();

        if (conn == null || conn.isClosed()) {
            conn = createConnection();
        }

        return conn;
    }

    public static synchronized void releaseConnection(Connection conn) {
        if (conn == null) return;

        try {
            if (!conn.isClosed()) {
                conn.setAutoCommit(true);
                pool.push(conn);
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseConfig] Failed to release connection.");
        }
    }

    private static Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
        conn.setAutoCommit(true);
        return conn;
    }
}
