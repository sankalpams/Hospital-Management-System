package lk.hms.common.security;

import java.security.SecureRandom;

/**
 * One-time passwords issued by administrators. 14 characters from an unambiguous alphabet
 * (no 0/O, 1/l/I), always containing letters and digits so they satisfy the password policy.
 */
public final class TemporaryPasswords {

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    public static String generate() {
        char[] out = new char[14];
        out[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        out[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 2; i < out.length; i++) {
            out[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        }
        for (int i = out.length - 1; i > 0; i--) {   // shuffle so positions are not predictable
            int j = RANDOM.nextInt(i + 1);
            char t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return new String(out);
    }
}
