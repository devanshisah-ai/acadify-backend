package com.acadify;

import com.sun.net.httpserver.HttpExchange;

public class SessionUtil {

    public static String[] extractAndValidate(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("[SessionUtil] Missing Authorization header");
            return null;
        }

        String token = authHeader.substring(7);

        try {
            String[] claims = JwtUtil.validate(token); // ✅ returns [userId, role]
            System.out.println("[SessionUtil] JWT valid for user " + claims[0] + " role " + claims[1]);
            return claims;
        } catch (Exception e) {
            System.out.println("[SessionUtil] JWT invalid: " + e.getMessage());
            return null;
        }
    }

    public static void invalidate(String token) {
        System.out.println("[SessionUtil] Logout — client clears token");
    }

    public static void invalidateSession(String token) {
        invalidate(token);
    }

    public static String createSession(String userId, String role) {
        try {
            return JwtUtil.generate(Integer.parseInt(userId), role);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isSessionValid(String token) {
        try {
            JwtUtil.validate(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}