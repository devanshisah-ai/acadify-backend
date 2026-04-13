package com.acadify;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PBKDF2Util {

    private static final int ITERATIONS  = 65536;
    private static final int KEY_LENGTH  = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    // ── Hash a plain-text password ────────────────────────────────────────────
    // Returns a string in format: "salt:hash"
    public static String hash(String password) throws Exception {
        // Generate random salt
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        // Hash using PBKDF2
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] hash = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();

        // Encode both salt and hash as Base64, store as "salt:hash"
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        return saltBase64 + ":" + hashBase64;
    }

    // ── Verify a plain-text password against a stored hash ───────────────────
    // storedHash must be in format: "salt:hash" (as produced by hash())
    public static boolean verify(String password, String storedHash) throws Exception {
        if (storedHash == null || !storedHash.contains(":")) {
            return false;
        }

        // Split stored value back into salt and hash
        String[] parts    = storedHash.split(":", 2);
        byte[] salt       = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

        // Hash the input password with the same salt
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        byte[] actualHash = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();

        // Constant-time comparison to prevent timing attacks
        if (expectedHash.length != actualHash.length) return false;
        int diff = 0;
        for (int i = 0; i < expectedHash.length; i++) {
            diff |= expectedHash[i] ^ actualHash[i];
        }

        return diff == 0;
    }
}