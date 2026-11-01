package review_engine.project.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Limits expensive synchronous AI endpoints by source IP per running instance.
 */
@Component
public class AiOperationRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_CLIENTS = 10_000;
    private final ConcurrentHashMap<String, Window> clients = new ConcurrentHashMap<>();
    private final AIProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiOperationRateLimitFilter(AIProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !"/api/insights/summary/generate".equals(path)
                && !"/api/dashboard/summary/generate".equals(path)
                && !(path.matches("/api/google-reviews/[0-9a-fA-F-]{36}/response-drafts"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        String clientId = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Window window = clients.get(clientId);
        if (window == null) {
            if (clients.size() >= MAX_CLIENTS) {
                cleanInactiveClients();
            }
            if (clients.size() >= MAX_CLIENTS) {
                writeLimited(response, 60);
                return;
            }
            Window candidate = new Window(now.getEpochSecond() / 60, now);
            Window previous = clients.putIfAbsent(clientId, candidate);
            window = previous == null ? candidate : previous;
        }
        long minute = now.getEpochSecond() / 60;
        long retryAfter = 0;
        synchronized (window) {
            window.lastSeen = now;
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= properties.getRateLimitPerMinute()) {
                retryAfter = Math.max(1, 60 - now.getEpochSecond() % 60);
            } else {
                window.count++;
            }
        }
        if (retryAfter == 0) {
            chain.doFilter(request, response);
            return;
        }
        writeLimited(response, retryAfter);
    }

    private void writeLimited(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(retryAfter));
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error("AI operation rate limit reached. Please retry later."));
    }

    @Scheduled(fixedDelay = 60_000)
    void cleanInactiveClients() {
        Instant threshold = clock.instant().minusSeconds(120);
        clients.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(threshold));
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
