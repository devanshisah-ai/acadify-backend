package com.acadify;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "acadify-super-secret-key-must-be-32-chars!!";
    private static final long EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    // ✅ Generate JWT token — no external library needed
    public static String generate(int userId, String role) throws Exception {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

        long now = System.currentTimeMillis();
        long exp = now + EXPIRY_MS;

        String payloadJson = "{"
            + "\"sub\":\"" + userId + "\","
            + "\"role\":\"" + role + "\","
            + "\"iat\":" + (now / 1000) + ","
            + "\"exp\":" + (exp / 1000)
            + "}";

        String payload   = base64Url(payloadJson);
        String signature = sign(header + "." + payload);

        return header + "." + payload + "." + signature;
    }

    // ✅ Validate JWT token — returns [userId, role] or throws if invalid
    public static String[] validate(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new Exception("Invalid token format");

        // ✅ Verify signature
        String expectedSig = sign(parts[0] + "." + parts[1]);
        if (!expectedSig.equals(parts[2])) {
            throw new Exception("Invalid token signature");
        }

        // ✅ Decode payload
        String payloadJson = new String(
            Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8
        );

        // ✅ Check expiry
        long exp = extractLong(payloadJson, "exp");
        if (exp * 1000 < System.currentTimeMillis()) {
            throw new Exception("Token expired");
        }

        String userId = extractString(payloadJson, "sub");
        String role   = extractString(payloadJson, "role");

        return new String[]{ userId, role };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String base64Url(String input) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static long extractLong(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0;
        start += search.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)))) end++;
        return Long.parseLong(json.substring(start, end));
    }
}