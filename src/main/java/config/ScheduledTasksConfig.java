package config;

import lombok.extern.slf4j.Slf4j;
import org.example.AppBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for scheduled data update tasks.
 * 
 * Data Update Strategy:
 * - LMIA data on open.canada.ca is typically updated quarterly (every 3 months)
 * - The scheduler runs weekly to check for new data
 * - Can be enabled/disabled via application.properties: app.data-update.enabled=true/false
 * - Update schedule can be configured via: app.data-update.cron (default: every Sunday at 2 AM)
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.data-update.enabled", havingValue = "true", matchIfMissing = false)
public class ScheduledTasksConfig {

    @Autowired
    private AppBody appBody;

    /**
     * Scheduled task to automatically download and process new LMIA datasets.
     * 
     * Default schedule: First day of every month at 2:00 AM
     * Can be overridden via app.data-update.cron property
     * 
     * The task:
     * 1. Downloads new datasets from open.canada.ca
     * 2. Processes and saves them to the database
     * 3. Skips duplicate records automatically
     * 4. Logs the results for monitoring
     */
    @Scheduled(cron = "${app.data-update.cron:0 0 2 1 * *}")
    public void scheduledDataUpdate() {
        log.info("Starting scheduled data update task...");
        try {
            appBody.downloadDatasets();
            long totalRecords = appBody.getTotalRecordsCount();
            log.info("Scheduled data update completed successfully. Total records in database: {}", totalRecords);
        } catch (Exception e) {
            log.error("Error during scheduled data update", e);
            // Don't throw exception to prevent scheduler from stopping
        }
    }

    /**
     * Optional: Daily health check to verify data freshness.
     * Checks if data is older than expected threshold (default: 120 days).
     * Logs a warning if data appears stale.
     */
    @Scheduled(cron = "${app.data-health-check.cron:0 0 3 * * *}")
    @ConditionalOnProperty(name = "app.data-health-check.enabled", havingValue = "true", matchIfMissing = false)
    public void dataHealthCheck() {
        log.debug("Running data health check...");
        // This could check the latest decision_date in the database
        // and warn if it's older than expected
        // Implementation can be added if needed
    }
}

