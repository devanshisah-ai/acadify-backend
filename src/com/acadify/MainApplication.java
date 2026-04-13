package com.acadify;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MainApplication {
    private static final int PORT = 8080;
    private static final String ALLOWED_ORIGIN = System.getenv("ALLOWED_ORIGIN") != null
            ? System.getenv("ALLOWED_ORIGIN")
            : "http://localhost:3000";

    private static final AuthController authController = new AuthController();

    public static void main(String[] args) {
        try {
            DatabaseConfig.initialize();
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(16));

            server.createContext("/auth/",    MainApplication::routeAuth);
            server.createContext("/student/", MainApplication::routeStudent);
            server.createContext("/teacher/", MainApplication::routeTeacher);
            server.createContext("/admin/",   MainApplication::routeAdmin);

            server.start();
            System.out.println("[Acadify] Server running on port " + PORT);
        } catch (IOException e) {
            System.err.println("[Acadify] FATAL: Failed to start server – " + e.getMessage());
            System.exit(1);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",      ALLOWED_ORIGIN);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods",     "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",     "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void routeAuth(HttpExchange exchange) {
        try {
            if (handlePreflight(exchange)) return;
            authController.handle(exchange);
        } catch (Exception e) {
            System.err.println("[MainApplication] Auth error: " + e.getMessage());
            try { ResponseUtil.sendError(exchange, 500, "Internal server error"); }
            catch (Exception ignored) {}
        }
    }

    private static void routeStudent(HttpExchange exchange) {
        try {
            if (handlePreflight(exchange)) return;

            int[] session = validateSession(exchange, "STUDENT");
            if (session == null) return;

            int userId    = session[0];
            int studentId = EntityResolver.resolveStudentId(userId);
            if (studentId == -1) {
                ResponseUtil.sendError(exchange, 404, "Student profile not found");
                return;
            }

            StudentController.handle(exchange, userId, studentId);
        } catch (Exception e) {
            System.err.println("[MainApplication] Student route error: " + e.getMessage());
            try { ResponseUtil.sendError(exchange, 500, "Internal server error"); }
            catch (Exception ignored) {}
        }
    }

    private static void routeTeacher(HttpExchange exchange) {
        try {
            if (handlePreflight(exchange)) return;

            int[] session = validateSession(exchange, "TEACHER");
            if (session == null) return;

            int userId    = session[0];
            int teacherId = EntityResolver.resolveTeacherId(userId);
            if (teacherId == -1) {
                ResponseUtil.sendError(exchange, 404, "Teacher profile not found");
                return;
            }

            TeacherController.handle(exchange, userId, teacherId);
        } catch (Exception e) {
            System.err.println("[MainApplication] Teacher route error: " + e.getMessage());
            try { ResponseUtil.sendError(exchange, 500, "Internal server error"); }
            catch (Exception ignored) {}
        }
    }

    private static void routeAdmin(HttpExchange exchange) {
        try {
            if (handlePreflight(exchange)) return;

            int[] session = validateSession(exchange, "ADMIN");
            if (session == null) return;

            AdminController.handle(exchange, session[0]);
        } catch (Exception e) {
            System.err.println("[MainApplication] Admin route error: " + e.getMessage());
            try { ResponseUtil.sendError(exchange, 500, "Internal server error"); }
            catch (Exception ignored) {}
        }
    }

    private static int[] validateSession(HttpExchange exchange, String requiredRole) throws IOException {
        String[] session = SessionUtil.extractAndValidate(exchange);

        if (session == null) {
            ResponseUtil.sendError(exchange, 401, "Unauthorized: Invalid or expired session");
            return null;
        }

        String userId   = session[0];
        String userRole = session[1];

        if (!requiredRole.equalsIgnoreCase(userRole)) {
            System.err.println("[SECURITY] Unauthorized access attempt: User with role '" +
                    userRole + "' tried to access '" + requiredRole + "' endpoint");
            ResponseUtil.sendError(exchange, 403, "Forbidden: Insufficient privileges");
            return null;
        }

        return new int[]{ Integer.parseInt(userId) };
    }
}