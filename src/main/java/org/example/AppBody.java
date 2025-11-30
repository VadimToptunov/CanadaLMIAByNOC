package org.example;

import dataProcessors.DataParser;
import dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.DatasetRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class AppBody {

    @Autowired
    private DatasetRepository datasetRepository;

    private static final File OUTPUT_DIRECTORY = new File("savedDatasets/NOCs/");

    private final DatasetDownloader datasetDownloader;
    private final DataParser dataParser;

    @Autowired
    public AppBody(DatasetDownloader datasetDownloader, DataParser dataParser) {
        this.datasetDownloader = datasetDownloader;
        this.dataParser = dataParser;
    }

    @Transactional
    public void downloadDatasets() {
        log.info("Starting dataset download process...");
        datasetDownloader.downloadFiles(OUTPUT_DIRECTORY);
        log.info("Download completed. Starting data processing...");
        processAndSaveDatasets();
    }

    @Transactional
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

        int totalProcessed = 0;
        int totalSaved = 0;

        for (File file : files) {
            if (!file.isFile()) continue;

            try {
                List<Dataset> datasets = new ArrayList<>();
                
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".csv")) {
                    datasets = dataParser.parseCsvFile(file);
                } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                    datasets = dataParser.parseExcelFile(file);
                } else {
                    log.debug("Skipping unsupported file type: {}", file.getName());
                    continue;
                }

                // Save to database, skipping duplicates (using batch insert for performance)
                int fileSaved = 0;
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
                    try {
                        datasetRepository.saveAll(datasetsToSave);
                        fileSaved = datasetsToSave.size();
                        totalSaved += fileSaved;
                    } catch (Exception e) {
                        log.error("Error batch saving datasets from file {}: {}", file.getName(), e.getMessage(), e);
                    }
                }

                totalProcessed += datasets.size();
                log.info("Processed file {}: {} records, {} saved", file.getName(), datasets.size(), fileSaved);

            } catch (Exception e) {
                log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
            }
        }

        log.info("Processing completed. Total processed: {}, Total saved: {}", totalProcessed, totalSaved);
    }

    private boolean isDuplicate(Dataset dataset) {
        // Check if a record with the same key fields already exists
        return datasetRepository.findByEmployerContainingIgnoreCase(dataset.getEmployer(), 
                org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent()
                .stream()
                .anyMatch(d -> d.getEmployer().equalsIgnoreCase(dataset.getEmployer()) &&
                              d.getNocCode().equals(dataset.getNocCode()) &&
                              d.getDecisionDate().equals(dataset.getDecisionDate()) &&
                              Objects.equals(d.getSourceFile(), dataset.getSourceFile()));
    }

    public long getTotalRecordsCount() {
        return datasetRepository.count();
    }
}