package com.acadify;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to build JSON strings for API responses.
 * Works with the normalized ResponseUtil to construct the "data" field.
 */
public class JsonBuilder {
    
    private final StringBuilder json;
    private boolean firstField;

    private JsonBuilder() {
        this.json = new StringBuilder("{");
        this.firstField = true;
    }

    /**
     * Create a new JSON object builder
     */
    public static JsonBuilder object() {
        return new JsonBuilder();
    }

    /**
     * Create a new JSON array builder
     */
    public static JsonArrayBuilder array() {
        return new JsonArrayBuilder();
    }

    /**
     * Add a string field
     */
    public JsonBuilder add(String key, String value) {
        addComma();
        json.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
        return this;
    }

    /**
     * Add an integer field
     */
    public JsonBuilder add(String key, int value) {
        addComma();
        json.append("\"").append(key).append("\":").append(value);
        return this;
    }

    /**
     * Add a long field
     */
    public JsonBuilder add(String key, long value) {
        addComma();
        json.append("\"").append(key).append("\":").append(value);
        return this;
    }

    /**
     * Add a double field
     */
    public JsonBuilder add(String key, double value) {
        addComma();
        json.append("\"").append(key).append("\":").append(value);
        return this;
    }

    /**
     * Add a BigDecimal field
     */
    public JsonBuilder add(String key, BigDecimal value) {
        addComma();
        json.append("\"").append(key).append("\":").append(value);
        return this;
    }

    /**
     * Add a boolean field
     */
    public JsonBuilder add(String key, boolean value) {
        addComma();
        json.append("\"").append(key).append("\":").append(value);
        return this;
    }

    /**
     * Add a Timestamp field
     */
    public JsonBuilder add(String key, Timestamp value) {
        addComma();
        json.append("\"").append(key).append("\":\"").append(value.toString()).append("\"");
        return this;
    }

    /**
     * Add a nullable string field
     */
    public JsonBuilder addNullable(String key, String value) {
        addComma();
        if (value == null) {
            json.append("\"").append(key).append("\":null");
        } else {
            json.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
        }
        return this;
    }

    /**
     * Add a raw JSON value (for nested objects or arrays)
     */
    public JsonBuilder addRaw(String key, String jsonValue) {
        addComma();
        json.append("\"").append(key).append("\":").append(jsonValue);
        return this;
    }

    /**
     * Build the final JSON string
     */
    public String build() {
        json.append("}");
        return json.toString();
    }

    private void addComma() {
        if (!firstField) {
            json.append(",");
        }
        firstField = false;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Builder for JSON arrays
     */
    public static class JsonArrayBuilder {
        private final List<String> items;

        private JsonArrayBuilder() {
            this.items = new ArrayList<>();
        }

        /**
         * Add a JSON object to the array
         */
        public JsonArrayBuilder add(String jsonObject) {
            items.add(jsonObject);
            return this;
        }

        /**
         * Add a JsonBuilder object to the array
         */
        public JsonArrayBuilder add(JsonBuilder builder) {
            items.add(builder.build());
            return this;
        }

        /**
         * Build the final JSON array string
         */
        public String build() {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) json.append(",");
                json.append(items.get(i));
            }
            json.append("]");
            return json.toString();
        }

        /**
         * Check if array is empty
         */
        public boolean isEmpty() {
            return items.isEmpty();
        }

        /**
         * Get array size
         */
        public int size() {
            return items.size();
        }
    }
}
