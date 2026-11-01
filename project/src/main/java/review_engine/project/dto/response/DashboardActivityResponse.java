package review_engine.project.dto.response;

import java.time.Instant;

/**
 * Business-scoped feedback delivery, conversation, and AI analysis lifecycle counts.
 */
public record DashboardActivityResponse(Instant from, Instant to, long totalRequests, long pendingRequests,
                                        long scheduledRequests, long sentRequests, long deliveredRequests,
                                        long failedRequests, long completedRequests, long expiredRequests,
                                        long conversationsInProgress, long conversationsCompleted,
                                        long analysisPending, long analysisFailed) {
}
