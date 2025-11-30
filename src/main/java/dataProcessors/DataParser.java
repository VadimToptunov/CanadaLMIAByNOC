package dataProcessors;

import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

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

    private static final Pattern NOC_PATTERN = Pattern.compile("(\\d{4})[\\s-]+(.+)");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(.+?),\\s*([A-Z]{2})\\s+([A-Z]\\d[A-Z]\\s?\\d[A-Z]\\d)");

    public List<Dataset> parseCsvFile(File file) {
        List<Dataset> datasets = new ArrayList<>();
        String sourceFileName = file.getName();
        
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            
            // Determine headers
            List<String> headers = csvParser.getHeaderNames();
            log.debug("CSV Headers: {}", headers);
            
            for (CSVRecord record : csvParser) {
                try {
                    Dataset dataset = parseRecord(record, headers, sourceFileName);
                    if (dataset != null) {
                        datasets.add(dataset);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing record {} in file {}: {}", record.getRecordNumber(), sourceFileName, e.getMessage());
                }
            }
            
            log.info("Parsed {} records from file {}", datasets.size(), sourceFileName);
        } catch (IOException e) {
            log.error("Error reading CSV file {}: {}", file.getName(), e.getMessage(), e);
        }
        
        return datasets;
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

    private Dataset parseRecord(CSVRecord record, List<String> headers, String sourceFile) {
        String province = getValue(record, headers, "Province/Territory", "Province");
        String stream = getValue(record, headers, "Stream");
        String employer = getValue(record, headers, "Employer");
        String address = getValue(record, headers, "Address");
        String nocInfo = getValue(record, headers, "Occupations under NOC 2011", "NOC", "NOC Code");
        String positionsStr = getValue(record, headers, "Positions Approved", "Positions");
        
        if (employer == null || employer.trim().isEmpty()) {
            return null;
        }
        
        // Parse NOC
        String nocCode = null;
        String nocTitle = null;
        if (nocInfo != null && !nocInfo.trim().isEmpty()) {
            Matcher matcher = NOC_PATTERN.matcher(nocInfo.trim());
            if (matcher.find()) {
                nocCode = matcher.group(1);
                nocTitle = matcher.group(2).trim();
            }
        }
        
        // Parse address
        String city = null;
        String postalCode = null;
        if (address != null && !address.trim().isEmpty()) {
            Matcher addressMatcher = ADDRESS_PATTERN.matcher(address);
            if (addressMatcher.find()) {
                city = addressMatcher.group(1).trim();
                postalCode = addressMatcher.group(3).replaceAll("\\s", "");
            } else {
                // Simple parsing if format doesn't match
                String[] parts = address.split(",");
                if (parts.length > 0) {
                    city = parts[0].trim();
                }
            }
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
        String nocInfo = getValueByIndex(values, headers, "Occupations under NOC 2011", "NOC", "NOC Code");
        String positionsStr = getValueByIndex(values, headers, "Positions Approved", "Positions");
        
        if (employer == null || employer.trim().isEmpty()) {
            return null;
        }
        
        // Parse NOC
        String nocCode = null;
        String nocTitle = null;
        if (nocInfo != null && !nocInfo.trim().isEmpty()) {
            Matcher matcher = NOC_PATTERN.matcher(nocInfo.trim());
            if (matcher.find()) {
                nocCode = matcher.group(1);
                nocTitle = matcher.group(2).trim();
            }
        }
        
        // Parse address
        String city = null;
        String postalCode = null;
        if (address != null && !address.trim().isEmpty()) {
            Matcher addressMatcher = ADDRESS_PATTERN.matcher(address);
            if (addressMatcher.find()) {
                city = addressMatcher.group(1).trim();
                postalCode = addressMatcher.group(3).replaceAll("\\s", "");
            } else {
                String[] parts = address.split(",");
                if (parts.length > 0) {
                    city = parts[0].trim();
                }
            }
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
        
        return dataset;
    }

    private String getValue(CSVRecord record, List<String> headers, String... possibleNames) {
        for (String name : possibleNames) {
            for (String header : headers) {
                if (header.equalsIgnoreCase(name)) {
                    String value = record.get(header);
                    return value != null && !value.trim().isEmpty() ? value : null;
                }
            }
        }
        return null;
    }

    private String getValueByIndex(List<String> values, List<String> headers, String... possibleNames) {
        for (String name : possibleNames) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equalsIgnoreCase(name) && i < values.size()) {
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

