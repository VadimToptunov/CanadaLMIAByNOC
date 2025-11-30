package config;

import lombok.extern.slf4j.Slf4j;
import org.example.AppBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import service.CompanyWebsiteService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Autowired
    private CompanyWebsiteService companyWebsiteService;

    @Value("${app.website-url-update.batch-size:50}")
    private int websiteUrlBatchSize;

    // Flag to prevent concurrent execution of scheduled tasks
    // Using AtomicBoolean for atomic check-and-set operations to prevent race conditions
    private final AtomicBoolean isUpdateInProgress = new AtomicBoolean(false);
    
    // Flag to prevent concurrent execution of website URL update tasks
    // Using AtomicBoolean for atomic check-and-set operations to prevent race conditions
    private final AtomicBoolean isWebsiteUrlUpdateInProgress = new AtomicBoolean(false);

    // Maximum timeout for data update task (24 hours - should be more than enough for monthly updates)
    private static final long UPDATE_TIMEOUT_HOURS = 24;

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
     * 
     * This method waits for the async operation to complete to prevent concurrent executions
     * and ensure proper error handling.
     */
    @Scheduled(cron = "${app.data-update.cron:0 0 2 1 * *}")
    public void scheduledDataUpdate() {
        // Atomically check and set flag to prevent concurrent execution
        // compareAndSet(expectedValue, newValue) returns true only if current value equals expectedValue
        // This ensures only one thread can set the flag from false to true
        if (!isUpdateInProgress.compareAndSet(false, true)) {
            log.warn("Scheduled data update skipped: previous update is still in progress");
            return;
        }

        log.info("Starting scheduled data update task...");

        try {
            // Start async download and wait for completion with timeout
            CompletableFuture<Void> future = appBody.downloadDatasetsAsync()
                    .thenRun(() -> {
                        long totalRecords = appBody.getTotalRecordsCount();
                        log.info("Scheduled data update completed successfully. Total records in database: {}", totalRecords);
                    })
                    .handle((result, ex) -> {
                        if (ex != null) {
                            log.error("Error during scheduled data update", ex);
                            // Re-throw as RuntimeException to propagate the error
                            // This ensures future.get() will throw an exception
                            throw new RuntimeException("Scheduled data update failed", ex);
                        }
                        return result;
                    });

            // Wait for completion with timeout to prevent concurrent executions
            // and ensure proper error propagation
            try {
                future.get(UPDATE_TIMEOUT_HOURS, TimeUnit.HOURS);
                log.info("Scheduled data update task finished successfully");
            } catch (TimeoutException e) {
                log.error("Scheduled data update timed out after {} hours", UPDATE_TIMEOUT_HOURS);
                // Cancel the future if possible
                future.cancel(true);
            } catch (Exception e) {
                log.error("Error waiting for scheduled data update to complete", e);
                // Exception is already logged, but we don't rethrow to prevent scheduler from stopping
            }
        } catch (Exception e) {
            log.error("Error starting scheduled data update", e);
            // Don't throw exception to prevent scheduler from stopping
        } finally {
            // Always clear the flag, even if there was an error
            // Use set(false) to reset the flag atomically
            isUpdateInProgress.set(false);
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

    /**
     * Scheduled task to find and update website URLs for companies that don't have them.
     * 
     * This task runs periodically to populate missing website URLs by searching the web.
     * It processes a limited number of companies per run to avoid rate limiting.
     * 
     * Default schedule: Every Sunday at 4:00 AM (after data updates)
     * Can be overridden via app.website-url-update.cron property
     * Can be enabled/disabled via app.website-url-update.enabled property
     */
    @Scheduled(cron = "${app.website-url-update.cron:0 0 4 * * SUN}")
    @ConditionalOnProperty(name = "app.website-url-update.enabled", havingValue = "true", matchIfMissing = false)
    public void scheduledWebsiteUrlUpdate() {
        // Atomically check and set flag to prevent concurrent execution
        // compareAndSet(expectedValue, newValue) returns true only if current value equals expectedValue
        // This ensures only one thread can set the flag from false to true
        if (!isWebsiteUrlUpdateInProgress.compareAndSet(false, true)) {
            log.warn("Scheduled website URL update skipped: previous update is still in progress");
            return;
        }

        log.info("Starting scheduled website URL update task...");

        try {
            int found = companyWebsiteService.findAndUpdateMissingUrls(websiteUrlBatchSize);
            log.info("Scheduled website URL update completed. Found {} website URLs", found);
        } catch (Exception e) {
            log.error("Error during scheduled website URL update", e);
            // Don't throw exception to prevent scheduler from stopping
        } finally {
            // Always clear the flag, even if there was an error
            // Use set(false) to reset the flag atomically
            isWebsiteUrlUpdateInProgress.set(false);
        }
    }
}

