package review_engine.project.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validated dashboard period, evidence threshold, and bounded result settings.
 */
@ConfigurationProperties(prefix = "review-engine.dashboard")
public class DashboardProperties {
    private int defaultPeriodDays = 30;
    private int maxDateRangeDays = 366;
    private int recurringIssueMinFeedbackCount = 2;
    private int defaultResultLimit = 20;

    public int getDefaultPeriodDays() {
        return defaultPeriodDays;
    }

    public void setDefaultPeriodDays(int defaultPeriodDays) {
        this.defaultPeriodDays = defaultPeriodDays;
    }

    public int getMaxDateRangeDays() {
        return maxDateRangeDays;
    }

    public void setMaxDateRangeDays(int maxDateRangeDays) {
        this.maxDateRangeDays = maxDateRangeDays;
    }

    public int getRecurringIssueMinFeedbackCount() {
        return recurringIssueMinFeedbackCount;
    }

    public void setRecurringIssueMinFeedbackCount(int recurringIssueMinFeedbackCount) {
        this.recurringIssueMinFeedbackCount = recurringIssueMinFeedbackCount;
    }

    public int getDefaultResultLimit() {
        return defaultResultLimit;
    }

    public void setDefaultResultLimit(int defaultResultLimit) {
        this.defaultResultLimit = defaultResultLimit;
    }

    @PostConstruct
    void validate() {
        if (defaultPeriodDays < 1 || defaultPeriodDays > 366
                || maxDateRangeDays < defaultPeriodDays || maxDateRangeDays > 366
                || recurringIssueMinFeedbackCount < 2 || recurringIssueMinFeedbackCount > 100
                || defaultResultLimit < 1 || defaultResultLimit > 100) {
            throw new IllegalStateException("Dashboard period, evidence threshold, or result limit is invalid.");
        }
    }
}
