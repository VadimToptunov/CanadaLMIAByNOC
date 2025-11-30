package dataProcessors;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;

@Slf4j
@Component
public class DatasetDownloader {

    private final Executor downloadTaskExecutor;
    
    @Value("${app.download.use-tor:false}")
    private boolean useTor;
    
    @Value("${app.download.tor-proxy-host:localhost}")
    private String torProxyHost;
    
    @Value("${app.download.tor-proxy-port:9050}")
    private int torProxyPort;

    public DatasetDownloader(@Qualifier("downloadTaskExecutor") Executor downloadTaskExecutor) {
        this.downloadTaskExecutor = downloadTaskExecutor;
    }
    
    @PostConstruct
    public void configureProxy() {
        if (useTor) {
            log.info("Configuring Tor SOCKS proxy: {}:{}", torProxyHost, torProxyPort);
            // Set Java system properties for SOCKS proxy
            // These properties are used by all HTTP connections, including RestAssured
            System.setProperty("socksProxyHost", torProxyHost);
            System.setProperty("socksProxyPort", String.valueOf(torProxyPort));
            // Enable SOCKS proxy for all protocols
            System.setProperty("java.net.useSystemProxies", "false");
            log.info("Tor SOCKS proxy configured successfully. All HTTP requests will be routed through Tor.");
            log.warn("Note: Make sure Tor is running on {}:{} before attempting downloads", torProxyHost, torProxyPort);
        } else {
            log.debug("Tor proxy is disabled");
        }
    }

    Map<String, String> createHeaders() {
        Map<String, String> headers = new TreeMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Postman-Token", UUID.randomUUID().toString());
        headers.put("Accept", "*/*");
        headers.put("Cache-Control", "no-cache");
        headers.put("Accept-Encoding", "gzip, deflate, br");

        return headers;
    }

    private Response sendRequestAndGetResponse(Map<String, String> headers) {
        return sendRequestWithRetry(headers, 3, 5000);
    }
    
    /**
     * Sends HTTP request with retry logic for handling connection timeouts and transient errors.
     * 
     * @param headers HTTP headers
     * @param maxRetries Maximum number of retry attempts
     * @param retryDelayMs Delay between retries in milliseconds
     * @return HTTP response
     */
    private Response sendRequestWithRetry(Map<String, String> headers, int maxRetries, long retryDelayMs) {
        baseURI = "https://open.canada.ca";
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Attempting to get dataset list (attempt {}/{})", attempt, maxRetries);
                Response response = given().when()
                        .headers(headers)
                        .queryParam("q", "lmia")
                        .get("/data/en/api/3/action/package_search");
                
                log.info("Sent request to get data on LMIA datasets by NOC.");
                
                // Check HTTP status code before processing
                int statusCode = response.getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    log.error("HTTP error {} when requesting dataset list from open.canada.ca API", statusCode);
                    log.debug("Response body: {}", response.prettyPrint());
                    throw new RuntimeException("Failed to retrieve dataset list: HTTP " + statusCode);
                }
                
                log.debug(response.prettyPrint());
                return response;
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Request failed (attempt {}/{}): {}", attempt, maxRetries, errorMsg);
                
                if (attempt < maxRetries) {
                    log.info("Retrying in {} ms...", retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                    // Exponential backoff: increase delay for next retry
                    retryDelayMs = (long) (retryDelayMs * 1.5);
                } else {
                    log.error("All {} attempts failed. Giving up.", maxRetries);
                }
            }
        }
        
        throw new RuntimeException("Failed to retrieve dataset list after " + maxRetries + " attempts", lastException);
    }

    private List<String> parseResponseAndExtractLinks(Response response) {
        List<String> urls = new ArrayList<>();
        
        // Verify response is successful before parsing JSON
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            log.error("Cannot parse response: HTTP status code {}", response.getStatusCode());
            return urls; // Return empty list
        }
        
        try {
            // Get all results from the API response
            List<LinkedHashMap<String, Object>> results = response.jsonPath().get("result.results");
            log.debug("Found {} datasets in API response", results != null ? results.size() : 0);
            
            if (results != null) {
                for (LinkedHashMap<String, Object> dataset : results) {
                    // Get resources from each dataset
                    Object resourcesObj = dataset.get("resources");
                    if (resourcesObj == null) {
                        continue;
                    }
                    
                    List<LinkedHashMap<String, Object>> resources;
                    if (resourcesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<LinkedHashMap<String, Object>> resourcesList = (List<LinkedHashMap<String, Object>>) resourcesObj;
                        resources = resourcesList;
                    } else {
                        continue;
                    }
                    
                    if (resources != null) {
                        for (LinkedHashMap<String, Object> r : resources) {
                            Object nameObj = r.get("name");
                            Object urlObj = r.get("url");
                            Object formatObj = r.get("format");
                            
                            // Null check for name and url fields
                            if (nameObj == null || urlObj == null) {
                                log.debug("Skipping resource with missing name or url field");
                                continue;
                            }
                            
                            String name = nameObj.toString();
                            String format = formatObj != null ? formatObj.toString().toUpperCase() : "";
                            String lcase = name.toLowerCase();
                            String urlString = urlObj.toString().toLowerCase();
                            
                            // Filter for CSV/Excel files related to LMIA/NOC, English only
                            // Check if it's a data file (CSV, Excel, or XLS) and contains relevant keywords
                            boolean isDataFile = format.equals("CSV") || format.equals("XLSX") || format.equals("XLS") ||
                                               lcase.endsWith(".csv") || lcase.endsWith(".xlsx") || lcase.endsWith(".xls") ||
                                               urlString.endsWith(".csv") || urlString.endsWith(".xlsx") || urlString.endsWith(".xls");
                            
                            // Check for English (must contain "en" and NOT contain French indicators)
                            // Exclude French files: check for "_fr", "/fr/", or "_f" before file extension (e.g., "file_f.csv")
                            // Also check for files ending with "_f." before extension
                            boolean hasFrenchIndicator = lcase.contains("_fr") || urlString.contains("_fr") || 
                                                         urlString.contains("/fr/") ||
                                                         (lcase.contains("_f.") && (lcase.endsWith(".csv") || lcase.endsWith(".xlsx") || lcase.endsWith(".xls"))) ||
                                                         (urlString.contains("_f.") && (urlString.endsWith(".csv") || urlString.endsWith(".xlsx") || urlString.endsWith(".xls")));
                            boolean isEnglish = (lcase.contains("en") || urlString.contains("_en") || urlString.contains("/en/")) &&
                                              !hasFrenchIndicator;
                            
                            // Check if relevant to LMIA/NOC/TFWP
                            boolean isRelevant = (lcase.contains("noc") || lcase.contains("lmia") || lcase.contains("tfwp") || 
                                                 urlString.contains("noc") || urlString.contains("lmia") || urlString.contains("tfwp"));
                            
                            if (isDataFile && isEnglish && isRelevant) {
                                log.info("Found file to download: {} (format: {})", name, format);
                                urls.add(urlObj.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing JSON response from API: {}", e.getMessage(), e);
            log.debug("Response body: {}", response.prettyPrint());
        }

        return urls;
    }

    private List<String> getCsvFilesLinks() {
        Map<String, String> headers = createHeaders();
        Response response = sendRequestAndGetResponse(headers);
        return parseResponseAndExtractLinks(response);
    }

    private void writeToFile(byte[] fileContents, File outputFile) {
        try (FileOutputStream outStream = new FileOutputStream(outputFile)) {
            outStream.write(fileContents);
            log.info(String.format("Writing to file %s", outputFile.getAbsolutePath()));
        } catch (Exception e) {
            log.info(String.format("Error writing to file %s", outputFile.getAbsolutePath()), e);
        }
    }

    /**
     * Downloads files asynchronously and in parallel for improved performance.
     * Multiple files are downloaded concurrently using CompletableFuture.
     * 
     * @param outputDirectory Directory where files will be saved
     */
    public void downloadFiles(File outputDirectory) {
        if (!outputDirectory.exists()) {
            boolean created = outputDirectory.mkdirs();
            if (!created) {
                log.error("Failed to create output directory: {}", outputDirectory.getAbsolutePath());
                return;
            }
        }
        
        List<String> urls = getCsvFilesLinks();
        log.info("Found {} files to download. Starting parallel download...", urls.size());
        
        if (urls.isEmpty()) {
            log.warn("No files to download");
            return;
        }
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        
        // Create parallel download tasks using the configured downloadTaskExecutor
        // This ensures the thread pool size, max pool size, and queue capacity settings are respected
        List<CompletableFuture<Void>> downloadTasks = urls.stream()
                .map(url -> {
                    try {
                        return CompletableFuture.runAsync(() -> {
                            try {
                                downloadSingleFile(url, outputDirectory, successCount, errorCount, skippedCount);
                            } catch (Exception e) {
                                errorCount.incrementAndGet();
                                log.error("Error downloading file from URL {}: {}", url, e.getMessage(), e);
                            }
                        }, downloadTaskExecutor);
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        // If task is rejected, create a completed future with error
                        errorCount.incrementAndGet();
                        log.warn("Download task rejected for URL {} (queue full): {}", url, e.getMessage());
                        CompletableFuture<Void> rejected = new CompletableFuture<>();
                        rejected.completeExceptionally(e);
                        return rejected;
                    }
                })
                .collect(Collectors.toList());
        
        // Wait for all downloads to complete (including rejected ones)
        CompletableFuture.allOf(downloadTasks.toArray(new CompletableFuture[0])).join();
        
        log.info("Download completed. Success: {}, Errors: {}, Skipped: {}, Total: {}", 
                successCount.get(), errorCount.get(), skippedCount.get(), urls.size());
    }
    
    /**
     * Downloads a single file from the given URL with retry logic.
     * 
     * @param url URL to download from
     * @param outputDirectory Directory to save the file
     * @param successCount Counter for successful downloads
     * @param errorCount Counter for failed downloads
     * @param skippedCount Counter for skipped downloads (already exists)
     */
    private void downloadSingleFile(String url, File outputDirectory, 
                                   AtomicInteger successCount, 
                                   AtomicInteger errorCount, 
                                   AtomicInteger skippedCount) {
        downloadSingleFileWithRetry(url, outputDirectory, successCount, errorCount, skippedCount, 3, 3000);
    }
    
    /**
     * Downloads a single file from the given URL with retry logic.
     * 
     * @param url URL to download from
     * @param outputDirectory Directory to save the file
     * @param successCount Counter for successful downloads
     * @param errorCount Counter for failed downloads
     * @param skippedCount Counter for skipped downloads (already exists)
     * @param maxRetries Maximum number of retry attempts
     * @param retryDelayMs Initial delay between retries in milliseconds
     */
    private void downloadSingleFileWithRetry(String url, File outputDirectory, 
                                            AtomicInteger successCount, 
                                            AtomicInteger errorCount, 
                                            AtomicInteger skippedCount,
                                            int maxRetries,
                                            long retryDelayMs) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        // Clean filename from query parameters
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf('?'));
        }
        File outputFile = new File(outputDirectory, fileName);
        
        // Skip if file already exists
        if (outputFile.exists()) {
            log.debug("File already exists, skipping: {}", fileName);
            skippedCount.incrementAndGet();
            return;
        }
        
        Exception lastException = null;
        long currentRetryDelay = retryDelayMs;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 1) {
                    log.debug("Retrying download of {} (attempt {}/{})", fileName, attempt, maxRetries);
                }
                
                Response response = given().when().get(url);
                
                // Check HTTP status code before processing
                int statusCode = response.getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    log.warn("HTTP error {} when downloading file from URL: {}. Skipping.", statusCode, url);
                    errorCount.incrementAndGet();
                    return;
                }
                
                byte[] fileContents = response.asByteArray();
                writeToFile(fileContents, outputFile);
                successCount.incrementAndGet();
                log.debug("Successfully downloaded: {}", fileName);
                return; // Success, exit method
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                
                if (attempt < maxRetries) {
                    log.warn("Error downloading {} (attempt {}/{}): {}. Retrying in {} ms...", 
                            fileName, attempt, maxRetries, errorMsg, currentRetryDelay);
                    try {
                        Thread.sleep(currentRetryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        errorCount.incrementAndGet();
                        log.error("Interrupted during retry delay for {}", fileName);
                        return;
                    }
                    // Exponential backoff: increase delay for next retry
                    currentRetryDelay = (long) (currentRetryDelay * 1.5);
                } else {
                    errorCount.incrementAndGet();
                    log.error("Error downloading file from URL {} after {} attempts: {}", 
                            url, maxRetries, errorMsg, lastException);
                }
            }
        }
    }
}