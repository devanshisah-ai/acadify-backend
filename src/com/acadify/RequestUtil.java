package com.acadify;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
public class RequestUtil {
    public static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        byte[] bytes = is.readAllBytes();
        is.close();
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static String[] parseJson(String json, String... keys) {
        String[] values = new String[keys.length];
        if (json == null || json.isBlank()) return values;
        for (int i = 0; i < keys.length; i++) {
            values[i] = extractValue(json, keys[i]);
        }
        return values;
    }
    public static String extractQueryParam(String query, String key) {
        if (query == null || query.isBlank()) return null;
        String searchKey = key + "=";
        int idx = query.indexOf(searchKey);
        if (idx == -1) return null;
        int valueStart = idx + searchKey.length();
        int valueEnd = query.indexOf('&', valueStart);
        if (valueEnd == -1) valueEnd = query.length();
        String value = query.substring(valueStart, valueEnd);
        return value.isBlank() ? null : value;
    }
    public static String extractValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(':', keyIndex + searchKey.length());
        if (colonIndex == -1) return null;
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        char firstChar = json.charAt(valueStart);
        if (firstChar == '"') {
            int valueEnd = findClosingQuote(json, valueStart + 1);
            if (valueEnd == -1) return null;
            return json.substring(valueStart + 1, valueEnd);
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r') break;
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd).trim();
    }
    private static int findClosingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '\\') {
                i++;
                continue;
            }
            if (json.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }
}