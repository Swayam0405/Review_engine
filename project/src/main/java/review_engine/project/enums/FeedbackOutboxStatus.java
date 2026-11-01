package review_engine.project.enums;

/**
 * Durable delivery state for asynchronous integration events.
 */
public enum FeedbackOutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
