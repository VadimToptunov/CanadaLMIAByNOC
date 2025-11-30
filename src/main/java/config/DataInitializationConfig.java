package config;

import lombok.extern.slf4j.Slf4j;
import org.example.AppBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for automatic data initialization on application startup.
 * 
 * This component automatically downloads and processes LMIA datasets when the application starts
 * if the database is empty. This is useful for Docker deployments where you want the data
 * to be loaded automatically without manual intervention.
 * 
 * Can be enabled/disabled via application.properties: app.data.auto.load.enabled=true/false
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.data.auto.load.enabled", havingValue = "true", matchIfMissing = false)
public class DataInitializationConfig implements ApplicationRunner {

    @Autowired
    private AppBody appBody;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking if database needs initial data load...");
        
        long recordCount = appBody.getTotalRecordsCount();
        
        if (recordCount == 0) {
            log.info("Database is empty. Starting automatic data download and processing...");
            try {
                // Start async download and wait for completion (with timeout)
                CompletableFuture<Void> future = appBody.downloadDatasetsAsync()
                        .thenRun(() -> {
                            long totalRecords = appBody.getTotalRecordsCount();
                            log.info("Automatic data initialization completed successfully. Total records in database: {}", totalRecords);
                        })
                        .handle((result, ex) -> {
                            if (ex != null) {
                                log.error("Error during automatic data initialization", ex);
                                throw new RuntimeException("Automatic data initialization failed", ex);
                            }
                            return result;
                        });

                // Wait for completion with timeout (30 minutes should be enough for initial load)
                try {
                    future.get(30, TimeUnit.MINUTES);
                    log.info("Automatic data initialization finished successfully");
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("Automatic data initialization timed out after 30 minutes");
                    future.cancel(true);
                } catch (Exception e) {
                    log.error("Error waiting for automatic data initialization to complete", e);
                }
            } catch (Exception e) {
                log.error("Error starting automatic data initialization", e);
            }
        } else {
            log.info("Database already contains {} records. Skipping automatic data initialization.", recordCount);
        }
    }
}

