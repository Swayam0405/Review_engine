package review_engine.project.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import review_engine.project.dto.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a bounded per-client-IP fixed-window limit to public feedback endpoints.
 */
@Component
public class PublicFeedbackRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_CLIENTS = 10_000;
    private final ConcurrentHashMap<String, Window> clients = new ConcurrentHashMap<>();
    private final FeedbackProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PublicFeedbackRateLimitFilter(FeedbackProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/public/feedback/")
                || !("GET".equals(request.getMethod()) || "POST".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        String clientId = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Window window = clients.get(clientId);
        if (window == null) {
            if (clients.size() >= MAX_CLIENTS) {
                cleanup(now);
            }
            if (clients.size() >= MAX_CLIENTS) {
                writeRateLimit(response, 60);
                return;
            }
            Window candidate = new Window(now.getEpochSecond() / 60, now);
            Window previous = clients.putIfAbsent(clientId, candidate);
            window = previous == null ? candidate : previous;
        }

        long minute = now.getEpochSecond() / 60;
        long retryAfter;
        boolean allowed;
        synchronized (window) {
            window.lastSeen = now;
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= properties.getRateLimitPerMinute()) {
                retryAfter = Math.max(1, 60 - now.getEpochSecond() % 60);
                allowed = false;
            } else {
                window.count++;
                retryAfter = 0;
                allowed = true;
            }
        }
        if (allowed) {
            chain.doFilter(request, response);
        } else {
            writeRateLimit(response, retryAfter);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void removeInactiveClients() {
        cleanup(clock.instant());
    }

    private void cleanup(Instant now) {
        Instant staleBefore = now.minusSeconds(120);
        clients.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(staleBefore));
    }

    private void writeRateLimit(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfter));
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error("Too many feedback requests. Please retry later."));
    }

    private static final class Window {
        private long minute;
        private int count;
        private volatile Instant lastSeen;

        private Window(long minute, Instant lastSeen) {
            this.minute = minute;
            this.lastSeen = lastSeen;
        }
    }
}
