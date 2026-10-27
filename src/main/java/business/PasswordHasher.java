package business;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int MAX_STORED_ITERATIONS = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password, salt, ITERATIONS, KEY_LENGTH);
        return HexFormat.of().formatHex(salt) + "$" + ITERATIONS + "$" + HexFormat.of().formatHex(key);
    }

    public static boolean verify(String password, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return BCrypt.checkpw(password, storedHash);
        }

        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            byte[] salt = HexFormat.of().parseHex(parts[0]);
            int iterations = Integer.parseInt(parts[1]);
            byte[] expectedKey = HexFormat.of().parseHex(parts[2]);
            if (salt.length != SALT_LENGTH || iterations < ITERATIONS || iterations > MAX_STORED_ITERATIONS
                    || expectedKey.length != KEY_LENGTH / Byte.SIZE) {
                return false;
            }
            byte[] actualKey = derive(password, salt, iterations, expectedKey.length * Byte.SIZE);
            return MessageDigest.isEqual(expectedKey, actualKey);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean needsUpgrade(String storedHash) {
        return storedHash != null && (storedHash.startsWith("$2a$")
                || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"));
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyLength) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
