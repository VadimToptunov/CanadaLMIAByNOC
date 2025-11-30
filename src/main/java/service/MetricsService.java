package service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for recording custom business metrics.
 * 
 * This service provides methods to track:
 * - Search operations and their performance
 * - Data processing statistics
 * - File download metrics
 * - Website URL lookup metrics
 * - Error rates
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a search operation.
     */
    public void recordSearch(String searchType) {
        Counter.builder("lmia.search.requests")
                .tag("type", searchType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a search error.
     */
    public void recordSearchError(String searchType, String errorType) {
        Counter.builder("lmia.search.errors")
                .tag("type", searchType)
                .tag("error", errorType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records dataset processing.
     */
    public void recordDatasetProcessed(int count, String fileType) {
        Counter.builder("lmia.dataset.processed")
                .tag("file_type", fileType)
                .register(meterRegistry)
                .increment(count);
    }

    /**
     * Records a file download.
     */
    public void recordFileDownload(boolean success) {
        if (success) {
            Counter.builder("lmia.file.downloads")
                    .description("Total number of files downloaded")
                    .register(meterRegistry)
                    .increment();
        } else {
            Counter.builder("lmia.file.downloads.errors")
                    .description("Total number of file download errors")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Records website URL lookup result.
     */
    public void recordWebsiteUrlLookup(boolean found) {
        if (found) {
            Counter.builder("lmia.website.url.found")
                    .description("Total number of website URLs found")
                    .register(meterRegistry)
                    .increment();
        } else {
            Counter.builder("lmia.website.url.not_found")
                    .description("Total number of website URLs not found")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Records search execution time.
     */
    public Timer.Sample startSearchTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops search timer and records the duration.
     */
    public void stopSearchTimer(Timer.Sample sample, String searchType) {
        Timer timer = Timer.builder("lmia.dataset.search")
                .tag("type", searchType)
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Records dataset processing time.
     */
    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops processing timer and records the duration.
     */
    public void stopProcessingTimer(Timer.Sample sample, String fileType) {
        Timer timer = Timer.builder("lmia.dataset.processing")
                .tag("file_type", fileType)
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Records file download time.
     */
    public Timer.Sample startDownloadTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops download timer and records the duration.
     */
    public void stopDownloadTimer(Timer.Sample sample) {
        Timer timer = Timer.builder("lmia.file.download")
                .description("Time taken to download files")
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Records website URL lookup time.
     */
    public Timer.Sample startUrlLookupTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stops URL lookup timer and records the duration.
     */
    public void stopUrlLookupTimer(Timer.Sample sample, boolean found) {
        Timer timer = Timer.builder("lmia.website.url.lookup")
                .tag("found", String.valueOf(found))
                .register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Records database query execution time.
     */
    public void recordDatabaseQuery(String queryType, long durationMs) {
        Timer.builder("lmia.database.query")
                .description("Database query execution time")
                .tag("query_type", queryType)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records cache hit/miss.
     */
    public void recordCacheOperation(String cacheName, boolean hit) {
        Counter.builder("lmia.cache.operations")
                .description("Cache operations")
                .tag("cache", cacheName)
                .tag("result", hit ? "hit" : "miss")
                .register(meterRegistry)
                .increment();
    }
}

