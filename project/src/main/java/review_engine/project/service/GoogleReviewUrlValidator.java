package review_engine.project.service;

import org.springframework.http.HttpStatus;
import review_engine.project.exception.ApiException;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Restricts configured review destinations to supported HTTPS Google review URL formats.
 */
public final class GoogleReviewUrlValidator {
    private static final Set<String> GOOGLE_HOSTS = Set.of(
            "g.page", "maps.app.goo.gl", "goo.gl", "www.google.com", "google.com",
            "search.google.com", "maps.google.com");

    private GoogleReviewUrlValidator() {
    }

    public static String validate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.strip()).normalize();
            String host = uri.getHost() == null ? null : IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !isGoogleReviewDestination(uri, host)) {
                throw new IllegalArgumentException("Unsupported review destination.");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Google review URL must be a supported HTTPS Google review destination.");
        }
    }

    public static boolean isValid(String value) {
        try {
            return validate(value) != null;
        } catch (ApiException exception) {
            return false;
        }
    }

    private static boolean isGoogleReviewDestination(URI uri, String host) {
        if (!GOOGLE_HOSTS.contains(host)) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getQuery() == null ? "" : uri.getQuery();
        return switch (host) {
            case "g.page" -> path.matches("/r/[^/]+/review/?");
            case "maps.app.goo.gl" -> !path.equals("/") && !path.isBlank();
            case "goo.gl" -> path.startsWith("/maps/") && path.length() > "/maps/".length();
            case "search.google.com" -> "/local/writereview".equals(path)
                    && query.matches("(?i).*\\bplaceid=[^&]+.*");
            case "www.google.com", "google.com" -> isMapsPlacePath(path)
                    && (query.matches("(?i).*\\bcid=[^&]+.*")
                    || query.matches("(?i).*\\bplaceid=[^&]+.*")
                    || path.startsWith("/maps/place/"));
            case "maps.google.com" -> isMapsPlacePath(path)
                    && (query.matches("(?i).*\\bcid=[^&]+.*")
                    || query.matches("(?i).*\\bplaceid=[^&]+.*")
                    || path.startsWith("/maps/place/"));
            default -> false;
        };
    }

    private static boolean isMapsPlacePath(String path) {
        return "/maps".equals(path) || path.startsWith("/maps/place/");
    }
}
