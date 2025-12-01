package service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for getting website URLs for companies.
 * 
 * This service first attempts to find real company website URLs using
 * CompanyWebsiteService, which searches the web and stores URLs in the database.
 * If no real URL is found, it generates a Google search URL as a fallback.
 */
@Slf4j
@Service
public class WebsiteUrlService {

    private final CompanyWebsiteService companyWebsiteService;

    @Autowired
    public WebsiteUrlService(CompanyWebsiteService companyWebsiteService) {
        this.companyWebsiteService = companyWebsiteService;
    }

    /**
     * Gets the website URL for a company.
     * First tries to find a real website URL from the database or via web search.
     * If not found, generates a Google search URL as a fallback.
     * 
     * @param companyName Name of the company
     * @param city City where the company is located (optional)
     * @param province Province where the company is located (optional)
     * @return Website URL if found, Google search URL as fallback, or null if company name is empty
     */
    public String getCompanyWebsiteUrl(String companyName, String city, String province) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }

        // During initial data loading, skip expensive web searches
        // Just check database for existing URLs and generate Google search URL as fallback
        // Real website URLs will be found later by scheduled task
        
        // Quick check: try to get existing URL from database (fast, no HTTP requests)
        String existingUrl = companyWebsiteService.getExistingWebsiteUrl(companyName);
        if (existingUrl != null && !existingUrl.contains("google.com/search")) {
            return existingUrl;
        }

        // Fallback: Generate a Google search URL (fast, no HTTP requests)
        return generateCompanySearchUrl(companyName, city, province);
    }

    /**
     * Generates a Google search URL for a company.
     * This is a fallback solution when actual website URLs are not available.
     * 
     * @param companyName Name of the company
     * @param city City where the company is located (optional)
     * @param province Province where the company is located (optional)
     * @return Google search URL for the company, or null if company name is empty
     */
    private String generateCompanySearchUrl(String companyName, String city, String province) {
        if (companyName == null || companyName.trim().isEmpty()) {
            return null;
        }

        try {
            // Build search query: "CompanyName" + "City" + "Province" + "Canada"
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("\"").append(companyName.trim()).append("\"");
            
            if (city != null && !city.trim().isEmpty()) {
                queryBuilder.append(" ").append(city.trim());
            }
            
            if (province != null && !province.trim().isEmpty()) {
                queryBuilder.append(" ").append(province.trim());
            }
            
            queryBuilder.append(" Canada website");
            
            String query = URLEncoder.encode(queryBuilder.toString(), StandardCharsets.UTF_8);
            String searchUrl = "https://www.google.com/search?q=" + query;
            
            log.debug("Generated search URL for company '{}': {}", companyName, searchUrl);
            return searchUrl;
        } catch (Exception e) {
            log.warn("Error generating search URL for company '{}': {}", companyName, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the website URL for a company using only the company name.
     * 
     * @param companyName Name of the company
     * @return Website URL if found, Google search URL as fallback, or null if company name is empty
     */
    public String getCompanyWebsiteUrl(String companyName) {
        return getCompanyWebsiteUrl(companyName, null, null);
    }

    /**
     * Extracts website URL from source data if available.
     * Currently, the source data from open.canada.ca doesn't include website URLs,
     * but this method can be used if the data format changes in the future.
     * 
     * @param sourceData Raw data from the source file
     * @return Website URL if found, null otherwise
     */
    public String extractWebsiteUrlFromSource(String sourceData) {
        if (sourceData == null || sourceData.trim().isEmpty()) {
            return null;
        }

        // Try to find URL patterns in the source data
        // Pattern: http:// or https:// followed by domain
        String urlPattern = "(https?://[^\\s]+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(sourceData);
        
        if (matcher.find()) {
            String url = matcher.group(1);
            // Remove trailing punctuation that might have been captured
            url = url.replaceAll("[.,;:!?]+$", "");
            log.debug("Extracted website URL from source data: {}", url);
            return url;
        }

        return null;
    }
}

