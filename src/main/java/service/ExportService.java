package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import repository.DatasetRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final DatasetRepository datasetRepository;

    public byte[] exportToCsv(String employer, String nocCode, String province, 
                              Dataset.DecisionStatus status) throws IOException {
        List<Dataset> datasets = fetchAllMatchingDatasets(employer, nocCode, province, status);
        
        StringWriter writer = new StringWriter();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("ID", "Province", "Stream", "Employer", "City", "Postal Code",
                        "NOC Code", "NOC Title", "Positions Approved", "Status", "Decision Date", "Source File")
                .build();
        
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {
            for (Dataset dataset : datasets) {
                csvPrinter.printRecord(
                        dataset.getId(),
                        dataset.getProvince(),
                        dataset.getStream(),
                        dataset.getEmployer(),
                        dataset.getCity(),
                        dataset.getPostalCode(),
                        dataset.getNocCode(),
                        dataset.getNocTitle(),
                        dataset.getPositionsApproved(),
                        dataset.getStatus(),
                        dataset.getDecisionDate(),
                        dataset.getSourceFile()
                );
            }
            csvPrinter.flush();
        }
        
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportToExcel(String employer, String nocCode, String province,
                                Dataset.DecisionStatus status) throws IOException {
        List<Dataset> datasets = fetchAllMatchingDatasets(employer, nocCode, province, status);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("LMIA Data");
            
            // Create styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "Province", "Stream", "Employer", "City", "Postal Code",
                    "NOC Code", "NOC Title", "Positions Approved", "Status", "Decision Date", "Source File"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int rowNum = 1;
            for (Dataset dataset : datasets) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(dataset.getId());
                row.createCell(1).setCellValue(dataset.getProvince());
                row.createCell(2).setCellValue(dataset.getStream());
                row.createCell(3).setCellValue(dataset.getEmployer());
                row.createCell(4).setCellValue(dataset.getCity() != null ? dataset.getCity() : "");
                row.createCell(5).setCellValue(dataset.getPostalCode() != null ? dataset.getPostalCode() : "");
                row.createCell(6).setCellValue(dataset.getNocCode());
                row.createCell(7).setCellValue(dataset.getNocTitle() != null ? dataset.getNocTitle() : "");
                row.createCell(8).setCellValue(dataset.getPositionsApproved());
                row.createCell(9).setCellValue(dataset.getStatus().name());
                row.createCell(10).setCellValue(dataset.getDecisionDate().toString());
                row.createCell(11).setCellValue(dataset.getSourceFile() != null ? dataset.getSourceFile() : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private List<Dataset> fetchAllMatchingDatasets(String employer, String nocCode, 
                                                   String province, Dataset.DecisionStatus status) {
        // Fetch all data in chunks if there are many records
        List<Dataset> allDatasets = new java.util.ArrayList<>();
        int pageSize = 1000;
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Dataset> pageResult = datasetRepository.searchDatasets(
                    employer, nocCode, province, status, null, null, pageable);
            
            allDatasets.addAll(pageResult.getContent());
            hasMore = pageResult.hasNext();
            page++;
            
            // Protection against infinite loop
            if (page > 100) {
                log.warn("Too many pages, limiting export to first 100,000 records");
                break;
            }
        }

        return allDatasets;
    }
}

