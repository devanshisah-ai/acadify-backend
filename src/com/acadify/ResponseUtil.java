package com.acadify;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ResponseUtil {

    // =========================
    // SUCCESS RESPONSES
    // =========================

    public static void sendSuccess(HttpExchange exchange, String message, Object data) throws IOException {
        String jsonData = (data == null) ? "null" : data.toString();
        String json = buildResponse(true, message, jsonData);
        sendResponse(exchange, 200, json);
    }

    public static void sendSuccess(HttpExchange exchange, String message) throws IOException {
        sendSuccess(exchange, message, null);
    }

    public static void sendCreated(HttpExchange exchange, String message, Object data) throws IOException {
        String jsonData = (data == null) ? "null" : data.toString();
        String json = buildResponse(true, message, jsonData);
        sendResponse(exchange, 201, json);
    }

    // =========================
    // ERROR RESPONSES
    // =========================

    public static void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 400, message);
    }

    public static void sendUnauthorized(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 401, message);
    }

    public static void sendForbidden(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 403, message);
    }

    public static void sendNotFound(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 404, message);
    }

    public static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Method not allowed");
    }

    public static void sendConflict(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 409, message);
    }

    public static void sendServerError(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 500, message);
    }

    // =========================
    // GENERIC ERROR
    // =========================

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = buildResponse(false, message, "null");
        sendResponse(exchange, statusCode, json);
    }

    // =========================
    // CORE RESPONSE BUILDER
    // =========================

    private static String buildResponse(boolean success, String message, String data) {
        return "{" +
                "\"success\":" + success + "," +
                "\"message\":\"" + escapeJson(message) + "\"," +
                "\"data\":" + data +
                "}";
    }

    // =========================
    // HTTP RESPONSE (no CORS — handled by MainApplication)
    // =========================

    private static void sendResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);

        // ✅ REMOVED: All CORS headers (MainApplication handles this)

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // =========================
    // RAW JSON
    // =========================

    public static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        // ✅ REMOVED: All CORS headers (MainApplication handles this)

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // =========================
    // JSON ESCAPE
    // =========================

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}