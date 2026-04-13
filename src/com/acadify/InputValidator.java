package com.acadify;
import java.util.regex.Pattern;
public class InputValidator {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final String[] VALID_ROLES = {"STUDENT", "TEACHER", "ADMIN"};
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_TEXT_LENGTH = 5000;
    public static String validateEmail(String email) throws ValidationException {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required");
        }
        email = email.trim();
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new ValidationException("Email is too long");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format");
        }
        return email.toLowerCase();
    }
    public static String validatePassword(String password) throws ValidationException {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password is required");
        }
        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ValidationException("Password is too long");
        }
        return password;
    }
    public static String validateRole(String role) throws ValidationException {
        if (role == null || role.isBlank()) {
            throw new ValidationException("Role is required");
        }
        role = role.toUpperCase().trim();
        for (String validRole : VALID_ROLES) {
            if (validRole.equals(role)) {
                return role;
            }
        }
        throw new ValidationException("Invalid role. Must be STUDENT, TEACHER, or ADMIN");
    }
    public static String validateName(String name) throws ValidationException {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Name is required");
        }
        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Name is too long");
        }
        if (!name.matches("^[A-Za-z][A-Za-z '-]*$")) {
            throw new ValidationException("Name contains invalid characters");
        }
        return name;
    }
    public static String validateText(String text, String fieldName) throws ValidationException {
        if (text == null || text.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        text = text.trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(fieldName + " is too long");
        }
        String lowerText = text.toLowerCase();
        if (containsSQLInjection(lowerText)) {
            throw new ValidationException("Invalid input detected");
        }
        return text;
    }
    public static int validateId(String idStr, String fieldName) throws ValidationException {
        if (idStr == null || idStr.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        try {
            int id = Integer.parseInt(idStr.trim());
            if (id <= 0) {
                throw new ValidationException(fieldName + " must be positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a valid number");
        }
    }
    public static double validateDecimal(String decimalStr, String fieldName, double min, double max)
            throws ValidationException {
        if (decimalStr == null || decimalStr.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        try {
            double value = Double.parseDouble(decimalStr.trim());
            if (value < min || value > max) {
                throw new ValidationException(fieldName + " must be between " + min + " and " + max);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a valid number");
        }
    }
    private static boolean containsSQLInjection(String input) {
        String[] sqlKeywords = {
            "' or '1'='1", "1=1", "drop table", "drop database",
            "exec(", "execute(", "union select", "insert into",
            "delete from", "update ", "xp_", "sp_",
            "--|", "", "';--", "' or 1=1", "admin'--"
        };
        for (String keyword : sqlKeywords) {
            if (input.contains(keyword)) {
                System.err.println("[SECURITY] Potential SQL injection detected: " + keyword);
                return true;
            }
        }
        return false;
    }
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
