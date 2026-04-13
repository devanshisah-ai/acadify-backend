package com.acadify;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class EnvConfig {

    private static final Map<String, String> envVars = new HashMap<>();

    static {
        // Load from .env file
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    envVars.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (Exception e) {
            System.out.println("No .env file found, falling back to system env.");
        }
    }

    // Get value — checks .env first, then system environment
    public static String get(String key) {
        return envVars.getOrDefault(key, System.getenv(key));
    }
}