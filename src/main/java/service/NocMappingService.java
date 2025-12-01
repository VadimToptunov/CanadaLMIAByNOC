package service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for mapping between NOC 2011 (4-digit) and NOC 2021 (5-digit) codes.
 * Provides methods to find equivalent NOC codes across different versions.
 */
@Slf4j
@Service
public class NocMappingService {

    /**
     * Gets all NOC codes that should match the given NOC code search.
     * This includes both the exact code and related codes from other NOC versions.
     * 
     * @param nocCode The NOC code to search for (can be 4-digit or 5-digit)
     * @return List of NOC codes to search for (including the original and related codes)
     */
    public List<String> getMatchingNocCodes(String nocCode) {
        if (nocCode == null || nocCode.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String trimmed = nocCode.trim();
        List<String> matchingCodes = new ArrayList<>();
        
        // Always include the exact code
        matchingCodes.add(trimmed);
        
        // If it's a 4-digit code (NOC 2011), also search for 5-digit codes that start with it
        if (trimmed.length() == 4) {
            // Add pattern for 5-digit codes starting with this 4-digit code
            // We'll use LIKE pattern in SQL: '0211%' to match 02110, 02111, etc.
            matchingCodes.add(trimmed + "%");
        }
        
        // If it's a 5-digit code (NOC 2021), also search for the 4-digit prefix
        if (trimmed.length() == 5) {
            String fourDigitPrefix = trimmed.substring(0, 4);
            matchingCodes.add(fourDigitPrefix);
        }
        
        // If it's a 6-digit code (future NOC 2026), also search for 4-digit and 5-digit prefixes
        if (trimmed.length() == 6) {
            String fourDigitPrefix = trimmed.substring(0, 4);
            String fiveDigitPrefix = trimmed.substring(0, 5);
            matchingCodes.add(fourDigitPrefix);
            matchingCodes.add(fiveDigitPrefix);
        }
        
        log.debug("NOC code '{}' maps to search codes: {}", nocCode, matchingCodes);
        return matchingCodes;
    }
    
    /**
     * Checks if a NOC code is a 4-digit (NOC 2011) code.
     */
    public boolean isNoc2011(String nocCode) {
        return nocCode != null && nocCode.trim().length() == 4;
    }
    
    /**
     * Checks if a NOC code is a 5-digit (NOC 2021) code.
     */
    public boolean isNoc2021(String nocCode) {
        return nocCode != null && nocCode.trim().length() == 5;
    }
}

