package review_engine.project.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables typed settings for the feedback and n8n subsystems.
 */
@Configuration
@EnableConfigurationProperties({FeedbackProperties.class, N8nProperties.class})
public class FeedbackConfiguration {
}
