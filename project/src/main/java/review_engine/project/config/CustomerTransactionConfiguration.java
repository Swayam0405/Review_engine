package review_engine.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the UTC clock used for transaction completion validation.
 */
@Configuration
public class CustomerTransactionConfiguration {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
