package pt.tecnico.pic.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Service responsible for password hashing, password verification,
 * temporary password generation and explicit clearing of sensitive char arrays.
 *
 * Passwords are never stored in plain text. The stored format is:
 * PBKDF2WithHmacSHA256$iterations$base64Salt$base64Hash
 */
public class PasswordService {
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int TEMPORARY_PASSWORD_LENGTH = 20;

    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/".toCharArray();
    private static final char[] ALL_TEMPORARY_PASSWORD_CHARS = (
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789" +
            "!@#$%^&*()-_=+[]{};:,.?/"
    ).toCharArray();

    private final SecureRandom secureRandom;

    public PasswordService() {
        this(new SecureRandom());
    }

    PasswordService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String hashPassword(char[] password) {
        requirePassword(password);

        byte[] salt = new byte[SALT_BYTES];
        byte[] hash = null;
        PBEKeySpec spec = null;

        try {
            secureRandom.nextBytes(salt);
            spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BITS);
            hash = SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded();

            return HASH_ALGORITHM + "$"
                    + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
            if (hash != null) {
                Arrays.fill(hash, (byte) 0);
            }
        }
    }

    public boolean verifyPassword(char[] password, String passwordHash) {
        if (password == null || password.length == 0 || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        byte[] salt = null;
        byte[] expectedHash = null;
        byte[] actualHash = null;
        PBEKeySpec spec = null;

        try {
            String[] parts = passwordHash.split("\\$", -1);
            if (parts.length != 4) {
                return false;
            }

            String algorithm = parts[0];
            if (!HASH_ALGORITHM.equals(algorithm)) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            if (iterations <= 0) {
                return false;
            }

            salt = Base64.getDecoder().decode(parts[2]);
            expectedHash = Base64.getDecoder().decode(parts[3]);
            if (salt.length == 0 || expectedHash.length == 0) {
                return false;
            }

            spec = new PBEKeySpec(password, salt, iterations, expectedHash.length * Byte.SIZE);
            actualHash = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
            if (actualHash != null) {
                Arrays.fill(actualHash, (byte) 0);
            }
        }
    }

    public char[] generateTemporaryPassword() {
        char[] password = new char[TEMPORARY_PASSWORD_LENGTH];

        password[0] = randomChar(UPPERCASE);
        password[1] = randomChar(LOWERCASE);
        password[2] = randomChar(DIGITS);
        password[3] = randomChar(SYMBOLS);

        for (int i = 4; i < password.length; i++) {
            password[i] = randomChar(ALL_TEMPORARY_PASSWORD_CHARS);
        }

        shuffle(password);
        return password;
    }

    public void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private char randomChar(char[] allowedChars) {
        return allowedChars[secureRandom.nextInt(allowedChars.length)];
    }

    private void shuffle(char[] value) {
        for (int i = value.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temporary = value[i];
            value[i] = value[j];
            value[j] = temporary;
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
    }
}
