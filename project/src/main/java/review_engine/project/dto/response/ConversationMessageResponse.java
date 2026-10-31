package review_engine.project.dto.response;

import review_engine.project.enums.ConversationMessageType;
import review_engine.project.enums.ConversationSenderType;

import java.time.Instant;

/**
 * Public-safe conversation message with no internal metadata.
 */
public record ConversationMessageResponse(
        ConversationSenderType senderType,
        ConversationMessageType messageType,
        String content,
        Instant createdAt) {
}
