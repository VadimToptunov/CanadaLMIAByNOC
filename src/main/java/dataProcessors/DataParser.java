package dataProcessors;

import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.WebsiteUrlService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DataParser {

    private final WebsiteUrlService websiteUrlService;

    @Autowired
    public DataParser(WebsiteUrlService websiteUrlService) {
        this.websiteUrlService = websiteUrlService;
    }

    // NOC pattern: supports old 4-digit (NOC 2011), new 5-digit (NOC 2021), and future versions
    // Examples: "0211 - Engineering managers" or "12104 - Employment insurance and revenue officers"
    // Using {4,} to support at least 4 digits but allow for potential future changes
    private static final Pattern NOC_PATTERN = Pattern.compile("(\\d{4,})[\\s-]+(.+)");
    // Pattern to match: "Street, City, Province PostalCode" or "Street, City, Province  PostalCode"
    // Example: "25 Trinity Street, St. John's, NL  A1E 2M3"
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(.+?),\\s*([^,]+),\\s*([A-Z]{2})\\s+([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)");
    
    // Canadian provinces and territories
    private static final String[] PROVINCES = {
        "Newfoundland and Labrador", "Ontario", "Quebec", "British Columbia",
        "Alberta", "Manitoba", "Saskatchewan", "Nova Scotia",
        "New Brunswick", "Prince Edward Island", "Yukon", "Northwest Territories",
        "Nunavut"
    };
    
    // Province abbreviations
    private static final String[] PROVINCE_ABBREVIATIONS = {
        "NL", "ON", "QC", "BC", "AB", "MB", "SK", "NS", "NB", "PE", "YT", "NT", "NU"
    };

    public List<Dataset> parseCsvFile(File file) {
        List<Dataset> datasets = new ArrayList<>();
        String sourceFileName = file.getName();
        
        try {
            // Smart parsing: handle files with different structures
            // Some files have province in separate lines before data blocks
            // Some files have province in a column
            // Some files have headers in different rows
            
            List<String> allLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    allLines.add(line);
                }
            }
            
            if (allLines.isEmpty()) {
                log.warn("CSV file {} is empty", sourceFileName);
                return datasets;
            }
            
            // Find header row and structure
            FileStructure structure = detectFileStructure(allLines);
            if (structure == null || structure.headers == null || structure.headers.isEmpty()) {
                log.warn("Could not detect structure of CSV file {}", sourceFileName);
                return datasets;
            }
            
            log.debug("Detected file structure: headerRow={}, hasProvinceInLine={}, province={}", 
                    structure.headerRowIndex, structure.hasProvinceInLine, structure.provinceFromFile);
            
            // Parse data rows
            String currentProvince = structure.provinceFromFile;
            
            for (int i = structure.headerRowIndex + 1; i < allLines.size(); i++) {
                String line = allLines.get(i);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                
                // Check if this line is a province header (for multi-province files)
                String detectedProvince = detectProvinceInLine(line);
                if (detectedProvince != null) {
                    currentProvince = detectedProvince;
                    log.debug("Found province section: {}", currentProvince);
                    continue;
                }
                
                // Check if this line is a header row (for multi-section files)
                if (isHeaderRow(line)) {
                    // Update headers for this section
                    try (CSVParser headerParser = CSVFormat.DEFAULT.parse(new StringReader(line))) {
                        CSVRecord headerRecord = headerParser.iterator().next();
                        structure.headers = new ArrayList<>();
                        for (String header : headerRecord) {
                            structure.headers.add(header != null ? header.trim() : "");
                        }
                        log.debug("Updated headers at line {}: {}", i + 1, structure.headers);
                    }
                    continue;
                }
                
                // Parse data row
                try (CSVParser rowParser = CSVFormat.DEFAULT.parse(new StringReader(line))) {
                    CSVRecord record = rowParser.iterator().next();
                    Dataset dataset = parseRecord(record, structure.headers, sourceFileName, currentProvince);
                    if (dataset != null) {
                        datasets.add(dataset);
                    }
                } catch (Exception e) {
                    log.debug("Error parsing line {} in file {}: {}", i + 1, sourceFileName, e.getMessage());
                }
            }
            
            log.info("Parsed {} records from file {}", datasets.size(), sourceFileName);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("header name is missing")) {
                log.warn("CSV file {} has missing or empty headers, skipping: {}", sourceFileName, e.getMessage());
            } else {
                log.error("Error parsing CSV file {}: {}", sourceFileName, e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Error reading CSV file {}: {}", file.getName(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error parsing CSV file {}: {}", sourceFileName, e.getMessage(), e);
        }
        
        return datasets;
    }
    
    /**
     * Detects the structure of a CSV file by analyzing the first lines.
     */
    private FileStructure detectFileStructure(List<String> lines) {
        FileStructure structure = new FileStructure();
        
        for (int i = 0; i < Math.min(lines.size(), 15); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            
            // Check if this is a header row
            if (isHeaderRow(line) && structure.headerRowIndex == -1) {
                try (CSVParser headerParser = CSVFormat.DEFAULT.parse(new StringReader(line))) {
                    CSVRecord headerRecord = headerParser.iterator().next();
                    structure.headers = new ArrayList<>();
                    for (String header : headerRecord) {
                        structure.headers.add(header != null ? header.trim() : "");
                    }
                    structure.headerRowIndex = i;
                    log.debug("Found header row at line {}: {}", i + 1, structure.headers);
                    
                    // Check previous line for province
                    if (i > 0) {
                        String prevLine = lines.get(i - 1).trim();
                        String province = detectProvinceInLine(prevLine);
                        if (province != null) {
                            structure.provinceFromFile = province;
                            structure.hasProvinceInLine = true;
                        }
                    }
                    break;
                } catch (Exception e) {
                    log.debug("Error parsing potential header line {}: {}", i + 1, e.getMessage());
                }
            }
        }
        
        return structure;
    }
    
    /**
     * Checks if a line looks like a header row.
     */
    private boolean isHeaderRow(String line) {
        String lower = line.toLowerCase();
        return lower.contains("employer") && 
               (lower.contains("address") || lower.contains("noc") || 
                lower.contains("province") || lower.contains("positions") ||
                lower.contains("stream"));
    }
    
    /**
     * Detects if a line contains a province name.
     */
    private String detectProvinceInLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = line.trim();
        // Skip lines that look like data rows (contain commas with addresses)
        if (trimmed.contains(",") && trimmed.length() > 50) {
            return null;
        }
        
        // Check for full province names
        for (String province : PROVINCES) {
            if (trimmed.equalsIgnoreCase(province) || trimmed.contains(province)) {
                return province;
            }
        }
        
        // Check for province abbreviations (but only if it's a standalone line)
        if (trimmed.length() <= 3) {
            for (String abbrev : PROVINCE_ABBREVIATIONS) {
                if (trimmed.equalsIgnoreCase(abbrev)) {
                    // Map abbreviation to full name
                    return mapAbbreviationToProvince(abbrev);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Maps province abbreviation to full name.
     */
    private String mapAbbreviationToProvince(String abbrev) {
        String[] abbrevs = {"NL", "ON", "QC", "BC", "AB", "MB", "SK", "NS", "NB", "PE", "YT", "NT", "NU"};
        String[] provinces = PROVINCES;
        for (int i = 0; i < abbrevs.length; i++) {
            if (abbrevs[i].equalsIgnoreCase(abbrev)) {
                return provinces[i];
            }
        }
        return abbrev;
    }
    
    /**
     * Internal class to hold file structure information.
     */
    private static class FileStructure {
        List<String> headers;
        int headerRowIndex = -1;
        String provinceFromFile;
        boolean hasProvinceInLine = false;
    }

    public List<Dataset> parseExcelFile(File file) {
        List<Dataset> datasets = new ArrayList<>();
        String sourceFileName = file.getName();
        
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                log.warn("Excel file {} has no data rows", sourceFileName);
                return datasets;
            }
            
            // Read headers from the first row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                log.warn("Excel file {} has null header row", sourceFileName);
                return datasets;
            }
            
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
            
            log.debug("Excel Headers: {}", headers);
            
            // Parse data starting from the second row
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    Dataset dataset = parseExcelRow(row, headers, sourceFileName);
                    if (dataset != null) {
                        datasets.add(dataset);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing row {} in file {}: {}", i + 1, sourceFileName, e.getMessage());
                }
            }
            
            log.info("Parsed {} records from Excel file {}", datasets.size(), sourceFileName);
        } catch (IOException e) {
            log.error("Error reading Excel file {}: {}", file.getName(), e.getMessage(), e);
        }
        
        return datasets;
    }

    private Dataset parseRecord(CSVRecord record, List<String> headers, String sourceFile, String provinceFromFile) {
        String province = getValue(record, headers, "Province/Territory", "Province");
        // Use province from file header if not found in record
        if ((province == null || province.trim().isEmpty()) && provinceFromFile != null) {
            province = provinceFromFile;
        }
        
        String stream = getValue(record, headers, "Stream");
        String employer = getValue(record, headers, "Employer");
        String address = getValue(record, headers, "Address");
        // Support NOC 2011 (4-digit), NOC 2021 (5-digit), and future NOC 2026 (5-digit)
        String nocInfo = getValue(record, headers, 
                "Occupations under NOC 2011", "Occupations under NOC 2021", "Occupations under NOC 2026",
                "NOC 2011", "NOC 2021", "NOC 2026",
                "NOC", "NOC Code", "National Occupational Classification");
        String positionsStr = getValue(record, headers, "Positions Approved", "Positions", "Positions requested");
        
        if (employer == null || employer.trim().isEmpty()) {
            return null;
        }
        
        // Parse NOC (supports 4-digit NOC 2011, 5-digit NOC 2021, and future versions)
        String nocCode = null;
        String nocTitle = null;
        if (nocInfo != null && !nocInfo.trim().isEmpty()) {
            Matcher matcher = NOC_PATTERN.matcher(nocInfo.trim());
            if (matcher.find()) {
                String extractedCode = matcher.group(1);
                // Validate: NOC codes are typically 4-6 digits (allow some flexibility for future)
                // Reject codes longer than 6 digits as they're likely not NOC codes
                if (extractedCode.length() >= 4 && extractedCode.length() <= 6) {
                    nocCode = extractedCode;
                    nocTitle = matcher.group(2).trim();
                } else {
                    log.debug("Skipping potential NOC code with unusual length {}: {}", extractedCode.length(), extractedCode);
                }
            }
        }
        
        // Parse address: "Street, City, Province PostalCode"
        // Example: "25 Trinity Street, St. John's, NL  A1E 2M3"
        // Or: "Street, City, Province  PostalCode" (with double space)
        String city = null;
        String postalCode = null;
        String provinceFromAddress = null;
        
        if (address != null && !address.trim().isEmpty()) {
            Matcher addressMatcher = ADDRESS_PATTERN.matcher(address);
            if (addressMatcher.find()) {
                // Group 1: Street, Group 2: City, Group 3: Province, Group 4: PostalCode
                city = addressMatcher.group(2).trim();
                String provAbbrev = addressMatcher.group(3).trim();
                postalCode = addressMatcher.group(4).replaceAll("\\s", "");
                provinceFromAddress = mapAbbreviationToProvince(provAbbrev);
            } else {
                // Fallback: try to extract city and province from address
                // Usually format is "Street, City, Province PostalCode" or "Street, City, Province"
                String[] parts = address.split(",");
                if (parts.length >= 2) {
                    // City is usually the second-to-last part before province/postal code
                    city = parts[parts.length - 2].trim();
                    
                    // Try to extract province and postal code from last part
                    if (parts.length >= 3) {
                        String lastPart = parts[parts.length - 1].trim();
                        // Check for postal code pattern
                        Matcher postalMatcher = Pattern.compile("([A-Z]{2})\\s+([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)").matcher(lastPart);
                        if (postalMatcher.find()) {
                            String provAbbrev = postalMatcher.group(1);
                            postalCode = postalMatcher.group(2).replaceAll("\\s", "");
                            provinceFromAddress = mapAbbreviationToProvince(provAbbrev);
                        } else {
                            // Just province abbreviation
                            if (lastPart.length() == 2) {
                                provinceFromAddress = mapAbbreviationToProvince(lastPart);
                            }
                        }
                    }
                } else if (parts.length > 0) {
                    // If only one part, it might be just the street
                    city = null;
                }
            }
        }
        
        // Use province from address if not found elsewhere
        if ((province == null || province.trim().isEmpty()) && provinceFromAddress != null) {
            province = provinceFromAddress;
        }
        
        // Parse number of positions
        Integer positions = parseInteger(positionsStr);
        if (positions == null || positions <= 0) {
            positions = 1; // Default value
        }
        
        // Extract date from filename
        LocalDate decisionDate = extractDateFromFileName(sourceFile);
        
        // Determine status (default APPROVED for positive files)
        Dataset.DecisionStatus status = sourceFile.toLowerCase().contains("negative") || 
                                       sourceFile.toLowerCase().contains("denied") ?
                                       Dataset.DecisionStatus.DENIED : 
                                       Dataset.DecisionStatus.APPROVED;
        
        Dataset dataset = new Dataset();
        dataset.setProvince(province != null ? province.trim() : "Unknown");
        dataset.setStream(stream != null ? stream.trim() : "Unknown");
        dataset.setEmployer(employer.trim());
        dataset.setCity(city);
        dataset.setPostalCode(postalCode);
        dataset.setNocCode(nocCode != null ? nocCode : "0000");
        dataset.setNocTitle(nocTitle);
        dataset.setPositionsApproved(positions);
        dataset.setStatus(status);
        dataset.setDecisionDate(decisionDate);
        dataset.setSourceFile(sourceFile);
        
        // Get website URL for the company
        // This will check the database first, then try to find it via web search,
        // and fall back to Google search URL if not found
        String websiteUrl = websiteUrlService.getCompanyWebsiteUrl(employer.trim(), city, province);
        dataset.setWebsiteUrl(websiteUrl);
        
        return dataset;
    }

    private Dataset parseExcelRow(Row row, List<String> headers, String sourceFile) {
        // Create temporary record for parsing
        List<String> values = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i);
            values.add(getCellValueAsString(cell));
        }
        
        // Use the same parsing logic as for CSV
        String province = getValueByIndex(values, headers, "Province/Territory", "Province");
        String stream = getValueByIndex(values, headers, "Stream");
        String employer = getValueByIndex(values, headers, "Employer");
        String address = getValueByIndex(values, headers, "Address");
        // Support NOC 2011 (4-digit), NOC 2021 (5-digit), and future NOC 2026 (5-digit)
        String nocInfo = getValueByIndex(values, headers, 
                "Occupations under NOC 2011", "Occupations under NOC 2021", "Occupations under NOC 2026",
                "NOC 2011", "NOC 2021", "NOC 2026",
                "NOC", "NOC Code", "National Occupational Classification");
        String positionsStr = getValueByIndex(values, headers, "Positions Approved", "Positions", "Positions requested");
        
        if (employer == null || employer.trim().isEmpty()) {
            return null;
        }
        
        // Parse NOC (supports 4-digit NOC 2011, 5-digit NOC 2021, and future versions)
        String nocCode = null;
        String nocTitle = null;
        if (nocInfo != null && !nocInfo.trim().isEmpty()) {
            Matcher matcher = NOC_PATTERN.matcher(nocInfo.trim());
            if (matcher.find()) {
                String extractedCode = matcher.group(1);
                // Validate: NOC codes are typically 4-6 digits (allow some flexibility for future)
                // Reject codes longer than 6 digits as they're likely not NOC codes
                if (extractedCode.length() >= 4 && extractedCode.length() <= 6) {
                    nocCode = extractedCode;
                    nocTitle = matcher.group(2).trim();
                } else {
                    log.debug("Skipping potential NOC code with unusual length {}: {}", extractedCode.length(), extractedCode);
                }
            }
        }
        
        // Parse address: same logic as CSV parsing
        String city = null;
        String postalCode = null;
        String provinceFromAddress = null;
        
        if (address != null && !address.trim().isEmpty()) {
            Matcher addressMatcher = ADDRESS_PATTERN.matcher(address);
            if (addressMatcher.find()) {
                // Group 1: Street, Group 2: City, Group 3: Province, Group 4: PostalCode
                city = addressMatcher.group(2).trim();
                String provAbbrev = addressMatcher.group(3).trim();
                postalCode = addressMatcher.group(4).replaceAll("\\s", "");
                provinceFromAddress = mapAbbreviationToProvince(provAbbrev);
            } else {
                // Fallback: try to extract city and province from address
                String[] parts = address.split(",");
                if (parts.length >= 2) {
                    city = parts[parts.length - 2].trim();
                    
                    if (parts.length >= 3) {
                        String lastPart = parts[parts.length - 1].trim();
                        Matcher postalMatcher = Pattern.compile("([A-Z]{2})\\s+([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)").matcher(lastPart);
                        if (postalMatcher.find()) {
                            String provAbbrev = postalMatcher.group(1);
                            postalCode = postalMatcher.group(2).replaceAll("\\s", "");
                            provinceFromAddress = mapAbbreviationToProvince(provAbbrev);
                        } else if (lastPart.length() == 2) {
                            provinceFromAddress = mapAbbreviationToProvince(lastPart);
                        }
                    }
                } else if (parts.length > 0) {
                    city = null;
                }
            }
        }
        
        // Use province from address if not found elsewhere
        if ((province == null || province.trim().isEmpty()) && provinceFromAddress != null) {
            province = provinceFromAddress;
        }
        
        Integer positions = parseInteger(positionsStr);
        if (positions == null || positions <= 0) {
            positions = 1;
        }
        
        LocalDate decisionDate = extractDateFromFileName(sourceFile);
        Dataset.DecisionStatus status = sourceFile.toLowerCase().contains("negative") || 
                                       sourceFile.toLowerCase().contains("denied") ?
                                       Dataset.DecisionStatus.DENIED : 
                                       Dataset.DecisionStatus.APPROVED;
        
        Dataset dataset = new Dataset();
        dataset.setProvince(province != null ? province.trim() : "Unknown");
        dataset.setStream(stream != null ? stream.trim() : "Unknown");
        dataset.setEmployer(employer.trim());
        dataset.setCity(city);
        dataset.setPostalCode(postalCode);
        dataset.setNocCode(nocCode != null ? nocCode : "0000");
        dataset.setNocTitle(nocTitle);
        dataset.setPositionsApproved(positions);
        dataset.setStatus(status);
        dataset.setDecisionDate(decisionDate);
        dataset.setSourceFile(sourceFile);
        
        // Get website URL for the company
        // This will check the database first, then try to find it via web search,
        // and fall back to Google search URL if not found
        String websiteUrl = websiteUrlService.getCompanyWebsiteUrl(employer.trim(), city, province);
        dataset.setWebsiteUrl(websiteUrl);
        
        return dataset;
    }

    private String getValue(CSVRecord record, List<String> headers, String... possibleNames) {
        for (String name : possibleNames) {
            for (String header : headers) {
                // Skip empty headers
                if (header == null || header.trim().isEmpty()) {
                    continue;
                }
                // Try exact match first
                if (header.equalsIgnoreCase(name)) {
                    try {
                        String value = record.get(header);
                        return value != null && !value.trim().isEmpty() ? value : null;
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
                // Try partial match for NOC fields (e.g., "Occupations under NOC 2021" contains "NOC")
                if (name.contains("NOC") && header.toLowerCase().contains("noc")) {
                    try {
                        String value = record.get(header);
                        return value != null && !value.trim().isEmpty() ? value : null;
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
            }
        }
        return null;
    }

    private String getValueByIndex(List<String> values, List<String> headers, String... possibleNames) {
        for (String name : possibleNames) {
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header == null) continue;
                
                // Try exact match first
                if (header.equalsIgnoreCase(name) && i < values.size()) {
                    String value = values.get(i);
                    return value != null && !value.trim().isEmpty() ? value : null;
                }
                // Try partial match for NOC fields
                if (name.contains("NOC") && header.toLowerCase().contains("noc") && i < values.size()) {
                    String value = values.get(i);
                    return value != null && !value.trim().isEmpty() ? value : null;
                }
            }
        }
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Check if it's an integer
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            // Remove all non-numeric characters except minus sign
            String cleaned = value.replaceAll("[^\\d-]", "");
            if (cleaned.isEmpty()) return null;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    LocalDate extractDateFromFileName(String fileName) {
        // Try to extract date from filename
        // Formats: 2017q1q2, 2018q3, tfwp_2019q1, TFWP_2021Q2, 2022q1
        Pattern datePattern = Pattern.compile("(\\d{4})[qQ]([1-4])");
        Matcher matcher = datePattern.matcher(fileName);
        
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int quarter = Integer.parseInt(matcher.group(2));
            
            // Validate quarter (must be 1-4)
            if (quarter < 1 || quarter > 4) {
                log.warn("Invalid quarter {} in filename {}, using current date", quarter, fileName);
                return LocalDate.now();
            }
            
            // Approximate date - middle of the quarter
            // Q1 -> February (2), Q2 -> May (5), Q3 -> August (8), Q4 -> November (11)
            int month = (quarter - 1) * 3 + 2;
            try {
                return LocalDate.of(year, month, 15);
            } catch (Exception e) {
                log.warn("Invalid date calculated from filename {}: year={}, quarter={}, month={}. Using current date.", 
                        fileName, year, quarter, month, e);
                return LocalDate.now();
            }
        }
        
        // If not found, return current date
        return LocalDate.now();
    }
}

