package org.example;

import nocservice.dataProcessors.DataParser;
import nocservice.dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.DatasetRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class AppBody {

    @Autowired
    private DatasetRepository datasetRepository;

    private static final File OUTPUT_DIRECTORY = new File("savedDatasets/NOCs/");

    private final DatasetDownloader datasetDownloader;
    private final DataParser dataParser;
    private final Executor downloadTaskExecutor;
    
    // Self-injection to ensure Spring AOP proxy is used for @Transactional methods
    // @Lazy breaks the circular dependency cycle
    @Autowired
    @Lazy
    private AppBody self;

    @Autowired
    public AppBody(DatasetDownloader datasetDownloader, 
                   DataParser dataParser,
                   @Qualifier("downloadTaskExecutor") Executor downloadTaskExecutor) {
        this.datasetDownloader = datasetDownloader;
        this.dataParser = dataParser;
        this.downloadTaskExecutor = downloadTaskExecutor;
    }

    /**
     * Downloads datasets from open.canada.ca and processes them asynchronously.
     * Note: This method is NOT transactional because it performs file I/O operations
     * (network downloads and file writes) which should not hold database connections.
     * Only processAndSaveDatasets() handles database transactions.
     * Uses self-injection to ensure @Transactional annotation is properly applied via Spring AOP proxy.
     * 
     * Processing is guaranteed to run even if download encounters errors, as long as some files were downloaded.
     * 
     * This method manually creates and manages a CompletableFuture instead of using @Async,
     * which allows proper async execution and correct future completion tracking.
     * 
     * @return CompletableFuture that completes when download and processing are finished
     */
    public CompletableFuture<Void> downloadDatasetsAsync() {
        log.info("Starting async dataset download process...");
        
        // Create a CompletableFuture that will be completed asynchronously
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // Execute the work asynchronously using the configured executor
        downloadTaskExecutor.execute(() -> {
            boolean downloadSucceeded = false;
            Exception downloadError = null;
            
            try {
                datasetDownloader.downloadFiles(OUTPUT_DIRECTORY);
                downloadSucceeded = true;
                log.info("Download completed successfully. Starting data processing...");
            } catch (Exception e) {
                downloadError = e;
                log.warn("Download encountered errors, but will attempt to process any downloaded files: {}", e.getMessage());
            }
            
            // Always attempt processing, even if download had errors
            // This ensures that partially downloaded files are still processed
            try {
                log.info("Starting data processing...");
                // Use self-injected proxy to ensure @Transactional is applied
                self.processAndSaveDatasets();
                log.info("Data processing completed successfully");
                
                if (downloadSucceeded) {
                    log.info("Async dataset download and processing completed successfully");
                    future.complete(null);
                } else {
                    log.warn("Processing completed, but download had errors. Some files may be missing.");
                    // Still complete successfully if processing worked, even if download had issues
                    future.complete(null);
                }
            } catch (Exception e) {
                log.error("Error during data processing", e);
                // If download also failed, combine errors
                if (downloadError != null) {
                    Exception combinedError = new RuntimeException("Both download and processing failed", e);
                    combinedError.addSuppressed(downloadError);
                    future.completeExceptionally(combinedError);
                } else {
                    future.completeExceptionally(e);
                }
            }
        });
        
        return future;
    }
    
    /**
     * Synchronous version for backward compatibility.
     * Downloads datasets and waits for completion.
     */
    public void downloadDatasets() {
        log.info("Starting dataset download process...");
        datasetDownloader.downloadFiles(OUTPUT_DIRECTORY);
        log.info("Download completed. Starting data processing...");
        // Use self-injected proxy to ensure @Transactional is applied
        self.processAndSaveDatasets();
    }

    public void processAndSaveDatasets() {
        if (!OUTPUT_DIRECTORY.exists() || !OUTPUT_DIRECTORY.isDirectory()) {
            log.warn("Output directory does not exist: {}", OUTPUT_DIRECTORY.getAbsolutePath());
            return;
        }

        File[] files = OUTPUT_DIRECTORY.listFiles();
        if (files == null || files.length == 0) {
            log.warn("No files found in directory: {}", OUTPUT_DIRECTORY.getAbsolutePath());
            return;
        }

        log.info("Found {} files to process", files.length);
        int totalProcessed = 0;
        int totalSaved = 0;
        int filesProcessed = 0;
        int filesWithErrors = 0;

        for (File file : files) {
            if (!file.isFile()) {
                log.debug("Skipping non-file: {}", file.getName());
                continue;
            }

            try {
                // Process and save each file in its own transaction
                int fileSaved = self.processAndSaveFile(file);
                totalSaved += fileSaved;
                filesProcessed++;
                
                // Count total processed (including duplicates)
                List<Dataset> datasets = new ArrayList<>();
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".csv")) {
                    datasets = dataParser.parseCsvFile(file);
                } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                    datasets = dataParser.parseExcelFile(file);
                }
                totalProcessed += datasets.size();
                
                log.info("Processed file {}: {} records parsed, {} saved to database", file.getName(), datasets.size(), fileSaved);

            } catch (Exception e) {
                log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
                filesWithErrors++;
                // Continue processing other files even if this one failed
            }
        }

        log.info("Processing completed. Files processed: {}, Files with errors: {}, Total records processed: {}, Total records saved: {}", 
                filesProcessed, filesWithErrors, totalProcessed, totalSaved);
    }
    
    @Transactional
    public int processAndSaveFile(File file) {
        try {
            List<Dataset> datasets = new ArrayList<>();
            
            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".csv")) {
                datasets = dataParser.parseCsvFile(file);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                datasets = dataParser.parseExcelFile(file);
            } else {
                log.debug("Skipping unsupported file type: {}", file.getName());
                return 0;
            }

            if (datasets.isEmpty()) {
                log.debug("No records parsed from file: {}", file.getName());
                return 0;
            }

            // Save to database, skipping duplicates (using batch insert for performance)
            List<Dataset> datasetsToSave = new ArrayList<>();
            
            for (Dataset dataset : datasets) {
                try {
                    // Check for duplicates by key fields
                    if (!isDuplicate(dataset)) {
                        datasetsToSave.add(dataset);
                    }
                } catch (Exception e) {
                    log.warn("Error checking duplicate for dataset from file {}: {}", file.getName(), e.getMessage());
                }
            }
            
            // Batch save for better performance
            if (!datasetsToSave.isEmpty()) {
                datasetRepository.saveAll(datasetsToSave);
                // Explicitly flush to ensure data is written to database
                datasetRepository.flush();
                log.debug("Successfully saved {} records from file {} to database", datasetsToSave.size(), file.getName());
                return datasetsToSave.size();
            }
            
            return 0;
        } catch (Exception e) {
            log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    private boolean isDuplicate(Dataset dataset) {
        // Check if a record with the same key fields already exists
        // Uses exact match query without pagination limit to find all duplicates
        return datasetRepository.existsByKeyFields(
                dataset.getEmployer(),
                dataset.getNocCode(),
                dataset.getDecisionDate(),
                dataset.getSourceFile());
    }

    public long getTotalRecordsCount() {
        return datasetRepository.count();
    }
}