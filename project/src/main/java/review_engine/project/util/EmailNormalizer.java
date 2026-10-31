package review_engine.project.util;

import java.util.Locale;

/**
 * Applies the project's canonical email representation before lookup or storage.
 */
public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    /**
     * Trims surrounding whitespace and lowercases an email address independent of host locale.
     *
     * @param email input email address
     * @return canonical lowercase address
     */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
