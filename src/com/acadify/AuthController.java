package com.acadify;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class AuthController implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.endsWith("/auth/register") && "POST".equals(method)) {
            handleRegister(exchange);
        } else if (path.endsWith("/auth/login") && "POST".equals(method)) {
            handleLogin(exchange);
        } else if (path.endsWith("/auth/logout") && "POST".equals(method)) {
            handleLogout(exchange);
        } else if (path.endsWith("/auth/me") && "GET".equals(method)) {
            handleMe(exchange);
        } else if (path.endsWith("/auth/hashpw") && "GET".equals(method)) {
            String query = exchange.getRequestURI().getQuery();
            String pw = (query != null && query.startsWith("pw=")) ? query.substring(3) : "";
            if (pw.isEmpty()) {
                sendJson(exchange, 400, "Missing pw parameter.");
            } else {
                try {
                    String hashed = PBKDF2Util.hash(pw);
                    sendJson(exchange, 200, hashed);
                } catch (Exception e) {
                    sendJson(exchange, 500, "Failed to hash password: " + e.getMessage());
                }
            }
        } else {
            sendResponse(exchange, 404, "Not found");
        }
    }

    // ── REGISTER ─────────────────────────────────────────────────────────────

    private static void handleRegister(HttpExchange exchange) throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8
        );

        JSONObject req = new JSONObject(body);

        String name     = req.optString("name",     "").trim();
        String email    = req.optString("email",    "").trim().toLowerCase();
        String password = req.optString("password", "");
        String role     = req.optString("role",     "STUDENT").toUpperCase();

        if ("ADMIN".equals(role)) {
            sendJson(exchange, 403, "Admin accounts cannot be self-registered.");
            return;
        }
        if (!"STUDENT".equals(role) && !"TEACHER".equals(role)) {
            sendJson(exchange, 400, "Invalid role. Must be STUDENT or TEACHER.");
            return;
        }
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            sendJson(exchange, 400, "All fields are required.");
            return;
        }
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            sendJson(exchange, 400, "Invalid email format.");
            return;
        }
        if (password.length() < 6) {
            sendJson(exchange, 400, "Password must be at least 6 characters.");
            return;
        }
        if (!password.matches(".*[A-Z].*")) {
            sendJson(exchange, 400, "Password must include at least one uppercase letter.");
            return;
        }
        if (!password.matches(".*[0-9].*")) {
            sendJson(exchange, 400, "Password must include at least one number.");
            return;
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            sendJson(exchange, 400, "Password must include at least one special character.");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {

            PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM users WHERE email = ?"
            );
            check.setString(1, email);
            if (check.executeQuery().next()) {
                sendJson(exchange, 400, "Email already registered.");
                return;
            }

            String hashedPassword = PBKDF2Util.hash(password);

            PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users (name, email, password, role, is_verified) " +
                "VALUES (?, ?, ?, ?, TRUE)"
            );
            insert.setString(1, name);
            insert.setString(2, email);
            insert.setString(3, hashedPassword);
            insert.setString(4, role);
            insert.executeUpdate();

            sendJson(exchange, 201, "Account created! You can now log in.");

        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, "Server error. Please try again.");
        }
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    private static void handleLogin(HttpExchange exchange) throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8
        );
        JSONObject req = new JSONObject(body);

        String email    = req.optString("email",    "").trim().toLowerCase();
        String password = req.optString("password", "");

        if (email.isEmpty() || password.isEmpty()) {
            sendJson(exchange, 400, "Email and password are required.");
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {

            PreparedStatement stmt = conn.prepareStatement(
                "SELECT user_id, name, password, role FROM users WHERE email = ?"
            );
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                sendJson(exchange, 401, "Invalid email or password.");
                return;
            }

            if (!PBKDF2Util.verify(password, rs.getString("password"))) {
                sendJson(exchange, 401, "Invalid email or password.");
                return;
            }

            int    userId = rs.getInt("user_id");
            String name   = rs.getString("name");
            String role   = rs.getString("role");

            // ✅ Generate JWT — no external library
            String token = JwtUtil.generate(userId, role);

            JSONObject data = new JSONObject();
            data.put("id",    userId);
            data.put("name",  name);
            data.put("role",  role);
            data.put("token", token);

            JSONObject response = new JSONObject();
            response.put("message", "Login successful");
            response.put("data", data);

            sendJsonObject(exchange, 200, response);

        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, "Server error. Please try again.");
        }
    }

    // ── /auth/me ──────────────────────────────────────────────────────────────

    private static void handleMe(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJson(exchange, 401, "Missing token.");
            return;
        }

        String token = authHeader.substring(7);

        try {
            // ✅ validate() now returns String[] { userId, role }
            String[] claims = JwtUtil.validate(token);
            int userId = Integer.parseInt(claims[0]);

            try (Connection conn = DatabaseConfig.getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, name, email, role FROM users WHERE user_id = ?"
                );
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    sendJson(exchange, 401, "User not found.");
                    return;
                }

                JSONObject data = new JSONObject();
                data.put("id",    rs.getInt("user_id"));
                data.put("name",  rs.getString("name"));
                data.put("email", rs.getString("email"));
                data.put("role",  rs.getString("role"));

                JSONObject response = new JSONObject();
                response.put("message", "Authenticated");
                response.put("data", data);

                sendJsonObject(exchange, 200, response);
            }

        } catch (Exception e) {
            sendJson(exchange, 401, "Invalid or expired session.");
        }
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    public static void handleLogout(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "Logged out successfully.");
    }

    // ── SHARED HELPERS ────────────────────────────────────────────────────────

    public static String hashPassword(String plainPassword) {
        try {
            return PBKDF2Util.hash(plainPassword);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public static void logActivity(Connection conn, int userId, String action,
                                   String details, int performedBy) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO activity_logs (user_id, action, details, performed_by, created_at) " +
                "VALUES (?, ?, ?, ?, NOW())"
            );
            stmt.setInt(1, userId);
            stmt.setString(2, action);
            stmt.setString(3, details);
            stmt.setInt(4, performedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to log activity: " + e.getMessage());
        }
    }

    public static void sendJson(HttpExchange exchange, int status, String message) throws IOException {
        JSONObject obj = new JSONObject();
        obj.put("message", message);
        byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    public static void sendJsonObject(HttpExchange exchange, int status, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    public static void sendResponse(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}