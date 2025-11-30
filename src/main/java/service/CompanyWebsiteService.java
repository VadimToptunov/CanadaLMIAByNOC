package service;

import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.DatasetRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for finding and storing real company website URLs.
 * 
 * This service searches for company websites using web search APIs
 * and stores the found URLs in the database to avoid redirecting users
 * to Google search every time.
 * 
 * Currently uses DuckDuckGo Instant Answer API (free, no API key required)
 * as a fallback. Can be extended to use Google Custom Search API,
 * Bing Search API, or other services.
 */
@Slf4j
@Service
public class CompanyWebsiteService {

    private final DatasetRepository datasetRepository;
    
    // Pattern to match URLs in HTML/text
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://(?:[\\w-]+\\.)+[\\w-]+(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]*)?"
    );
    
    // Pattern to match common non-website URLs (social media, etc.)
    private static final Pattern EXCLUDE_PATTERN = Pattern.compile(
        "(facebook|twitter|linkedin|instagram|youtube|google\\.com/search|bing\\.com/search)\\.com"
    );

    @Autowired
    public CompanyWebsiteService(DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
    }

    /**
     * Gets the website URL for a company, checking the database first.
     * If not found, attempts to find it via web search.
     * 
     * @param companyName Name of the company
     * @param city City where the company is located (optional)
     * @param province Province where the company is located (optional)
     * @return Website URL if found, null otherwise
     */
    @Transactional
    public String getOrFindCompanyWebsiteUrl(String companyName, String city, String province) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }

        String normalizedCompanyName = companyName.trim();
        
        // First, check if we already have a URL for this company in the database
        List<Object[]> existingUrls = datasetRepository.findCompanyWebsiteUrl(normalizedCompanyName);
        if (!existingUrls.isEmpty() && existingUrls.get(0)[1] != null) {
            String existingUrl = existingUrls.get(0)[1].toString();
            // Skip Google search URLs - we want real websites
            if (!existingUrl.contains("google.com/search")) {
                log.debug("Found existing website URL for company '{}': {}", companyName, existingUrl);
                return existingUrl;
            }
        }

        // Try to find the website URL
        String websiteUrl = findCompanyWebsiteUrl(normalizedCompanyName, city, province);
        
        if (websiteUrl != null && !websiteUrl.contains("google.com/search")) {
            // Save the found URL to all records for this company
            updateCompanyWebsiteUrl(normalizedCompanyName, websiteUrl);
            log.info("Found and saved website URL for company '{}': {}", companyName, websiteUrl);
        }

        return websiteUrl;
    }

    /**
     * Attempts to find a company's website URL using web search.
     * 
     * @param companyName Name of the company
     * @param city City where the company is located (optional)
     * @param province Province where the company is located (optional)
     * @return Website URL if found, null otherwise
     */
    private String findCompanyWebsiteUrl(String companyName, String city, String province) {
        try {
            // Try DuckDuckGo Instant Answer API first (free, no API key)
            String url = searchDuckDuckGo(companyName, city, province);
            if (url != null && isValidWebsiteUrl(url)) {
                return url;
            }

            // Fallback: Try to extract from Google search results (HTML scraping)
            // Note: This is a simple implementation. For production, consider using
            // Google Custom Search API (requires API key) or other services
            url = searchGoogle(companyName, city, province);
            if (url != null && isValidWebsiteUrl(url)) {
                return url;
            }

        } catch (Exception e) {
            log.warn("Error searching for website URL for company '{}': {}", companyName, e.getMessage());
        }

        return null;
    }

    /**
     * Searches DuckDuckGo Instant Answer API for company website.
     * DuckDuckGo provides a free API without requiring an API key.
     */
    private String searchDuckDuckGo(String companyName, String city, String province) {
        try {
            // Build search query
            StringBuilder query = new StringBuilder();
            query.append("\"").append(companyName).append("\"");
            if (city != null && !city.trim().isEmpty()) {
                query.append(" ").append(city);
            }
            if (province != null && !province.trim().isEmpty()) {
                query.append(" ").append(province);
            }
            query.append(" Canada website");

            String encodedQuery = URLEncoder.encode(query.toString(), StandardCharsets.UTF_8);
            String apiUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    // Parse JSON response (simple parsing - for production use a JSON library)
                    String json = response.toString();
                    
                    // Look for "AbstractURL" or "Results" in the response
                    Pattern abstractUrlPattern = Pattern.compile("\"AbstractURL\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = abstractUrlPattern.matcher(json);
                    if (matcher.find()) {
                        String foundUrl = matcher.group(1);
                        if (isValidWebsiteUrl(foundUrl)) {
                            return foundUrl;
                        }
                    }

                    // Try Results array
                    Pattern resultsPattern = Pattern.compile("\"Results\"\\s*:\\s*\\[\\s*\\{[^}]*\"FirstURL\"\\s*:\\s*\"([^\"]+)\"");
                    matcher = resultsPattern.matcher(json);
                    if (matcher.find()) {
                        String foundUrl = matcher.group(1);
                        if (isValidWebsiteUrl(foundUrl)) {
                            return foundUrl;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("DuckDuckGo search failed for company '{}': {}", companyName, e.getMessage());
        }

        return null;
    }

    /**
     * Searches Google and attempts to extract the first result URL.
     * Note: This is a simple implementation. For production, use Google Custom Search API.
     */
    private String searchGoogle(String companyName, String city, String province) {
        try {
            // Build search query
            StringBuilder query = new StringBuilder();
            query.append("\"").append(companyName).append("\"");
            if (city != null && !city.trim().isEmpty()) {
                query.append(" ").append(city);
            }
            if (province != null && !province.trim().isEmpty()) {
                query.append(" ").append(province);
            }
            query.append(" Canada site:ca OR site:com");

            String encodedQuery = URLEncoder.encode(query.toString(), StandardCharsets.UTF_8);
            String searchUrl = "https://www.google.com/search?q=" + encodedQuery + "&num=5";

            HttpURLConnection connection = (HttpURLConnection) new URL(searchUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder html = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        html.append(line);
                    }

                    // Extract URLs from search results
                    // Google search results contain URLs in href attributes
                    Pattern hrefPattern = Pattern.compile("href=\"(https?://[^\"]+)\"");
                    Matcher matcher = hrefPattern.matcher(html.toString());
                    
                    while (matcher.find()) {
                        String url = matcher.group(1);
                        // Skip Google's own URLs
                        if (url.contains("/url?q=")) {
                            // Extract actual URL from Google redirect
                            int start = url.indexOf("url?q=") + 6;
                            int end = url.indexOf("&", start);
                            if (end == -1) end = url.length();
                            url = url.substring(start, end);
                            url = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
                        }
                        
                        if (isValidWebsiteUrl(url)) {
                            return url;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Google search failed for company '{}': {}", companyName, e.getMessage());
        }

        return null;
    }

    /**
     * Validates if a URL is a valid website URL (not a search engine, social media, etc.)
     */
    private boolean isValidWebsiteUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        url = url.toLowerCase();
        
        // Exclude search engines and social media
        if (EXCLUDE_PATTERN.matcher(url).find()) {
            return false;
        }

        // Must be http or https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // Should be a proper domain
        if (!URL_PATTERN.matcher(url).matches()) {
            return false;
        }

        return true;
    }

    /**
     * Updates website URL for all records of a company.
     */
    @Transactional
    public void updateCompanyWebsiteUrl(String companyName, String websiteUrl) {
        List<Dataset> records = datasetRepository.findRecordsNeedingWebsiteUrl(companyName);
        
        for (Dataset record : records) {
            record.setWebsiteUrl(websiteUrl);
        }
        
        if (!records.isEmpty()) {
            datasetRepository.saveAll(records);
            log.debug("Updated website URL for {} records of company '{}'", records.size(), companyName);
        }
    }

    /**
     * Finds and updates website URLs for companies that don't have them.
     * This method can be called periodically to populate missing URLs.
     * 
     * @param limit Maximum number of companies to process
     * @return Number of companies for which URLs were found
     */
    @Transactional
    public int findAndUpdateMissingUrls(int limit) {
        List<String> companies = datasetRepository.findCompaniesWithoutWebsiteUrl();
        
        int found = 0;
        int processed = 0;
        
        for (String companyName : companies) {
            if (processed >= limit) {
                break;
            }
            
            String url = getOrFindCompanyWebsiteUrl(companyName, null, null);
            if (url != null && !url.contains("google.com/search")) {
                found++;
            }
            
            processed++;
            
            // Add small delay to avoid rate limiting
            try {
                Thread.sleep(1000); // 1 second delay between requests
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("Processed {} companies, found {} website URLs", processed, found);
        return found;
    }
}

