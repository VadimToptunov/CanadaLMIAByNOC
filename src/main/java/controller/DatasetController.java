package controller;

import dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Dataset;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repository.DatasetRepository;
import service.ExportService;
import service.MetricsService;
import service.ReferenceDataService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Dataset Search", description = "Public API endpoints for searching LMIA datasets")
public class DatasetController {

    private final DatasetRepository datasetRepository;
    private final ExportService exportService;
    private final MetricsService metricsService;
    private final ReferenceDataService referenceDataService;

    @Operation(
            summary = "Search datasets with filters",
            description = "Search LMIA datasets using multiple filters: employer name, NOC code, province, status, and date range. Returns paginated results."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Search completed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters"
            )
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<DatasetDTO>>> searchDatasets(
            @Valid SearchRequest request) {
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startSearchTimer();
        String searchType = determineSearchType(request);
        
        try {
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
            String statusString = null;
            
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                try {
                    Dataset.DecisionStatus.valueOf(request.getStatus().toUpperCase());
                    statusString = request.getStatus().toUpperCase();
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status parameter: {}", request.getStatus());
                    metricsService.recordSearchError(searchType, "invalid_status");
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid status parameter. Use APPROVED or DENIED"));
                }
            }

            long startTime = System.currentTimeMillis();
            Page<Dataset> results = datasetRepository.searchDatasets(
                    request.getEmployer(), request.getNocCode(), request.getProvince(), 
                    statusString, request.getStartDate(), request.getEndDate(), pageable);
            metricsService.recordDatabaseQuery("search", System.currentTimeMillis() - startTime);

            List<DatasetDTO> dtoList = results.getContent().stream()
                    .map(DatasetDTO::fromEntity)
                    .collect(Collectors.toList());

            PagedResponse<DatasetDTO> pagedResponse = PagedResponse.of(
                    dtoList,
                    results.getTotalElements(),
                    results.getTotalPages(),
                    results.getNumber(),
                    results.getSize(),
                    results.hasNext(),
                    results.hasPrevious()
            );

            metricsService.recordSearch(searchType);
            return ResponseEntity.ok(ApiResponse.success(pagedResponse));
        } catch (Exception e) {
            metricsService.recordSearchError(searchType, e.getClass().getSimpleName());
            throw e;
        } finally {
            metricsService.stopSearchTimer(sample, searchType);
        }
    }
    
    private String determineSearchType(SearchRequest request) {
        if (request.getEmployer() != null) return "employer";
        if (request.getNocCode() != null) return "noc";
        if (request.getProvince() != null) return "province";
        return "general";
    }

    @GetMapping("/search/legacy")
    public ResponseEntity<ApiResponse<PagedResponse<DatasetDTO>>> searchDatasetsLegacy(
            @RequestParam(required = false) String employer,
            @RequestParam(required = false) String nocCode,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        SearchRequest request = new SearchRequest();
        request.setEmployer(employer);
        request.setNocCode(nocCode);
        request.setProvince(province);
        request.setStatus(status);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPage(page);
        request.setSize(size);

        return searchDatasets(request);
    }

    @Operation(
            summary = "Search by employer name",
            description = "Search for all LMIA records for a specific employer. Supports partial name matching (case-insensitive)."
    )
    @GetMapping("/employer/{employerName}")
    public ResponseEntity<ApiResponse<PagedResponse<DatasetDTO>>> searchByEmployer(
            @Parameter(description = "Employer name (partial match supported)", required = true)
            @PathVariable String employerName,
            @RequestParam(defaultValue = "0") @Valid @Min(0) int page,
            @RequestParam(defaultValue = "20") @Valid @Min(1) int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Dataset> results = datasetRepository.findByEmployerContainingIgnoreCase(employerName, pageable);

        List<DatasetDTO> dtoList = results.getContent().stream()
                .map(DatasetDTO::fromEntity)
                .collect(Collectors.toList());

        PagedResponse<DatasetDTO> pagedResponse = PagedResponse.of(
                dtoList,
                results.getTotalElements(),
                results.getTotalPages(),
                results.getNumber(),
                results.getSize(),
                results.hasNext(),
                results.hasPrevious()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @Operation(
            summary = "Search by NOC code",
            description = "Search for all LMIA records for a specific National Occupational Classification (NOC) code. " +
                    "Supports both NOC 2011 (4-digit) and NOC 2021 (5-digit) codes. " +
                    "Searching by 4-digit code will also find matching 5-digit codes, and vice versa."
    )
    @GetMapping("/noc/{nocCode}")
    public ResponseEntity<ApiResponse<PagedResponse<DatasetDTO>>> searchByNoc(
            @Parameter(description = "NOC code (e.g., 0211 for NOC 2011 or 12104 for NOC 2021)", required = true)
            @PathVariable String nocCode,
            @RequestParam(defaultValue = "0") @Valid @Min(0) int page,
            @RequestParam(defaultValue = "20") @Valid @Min(1) int size) {

        Pageable pageable = PageRequest.of(page, size);
        // Use cross-version search to find both old and new NOC codes
        Page<Dataset> results = datasetRepository.findByNocCodeCrossVersion(nocCode, pageable);

        List<DatasetDTO> dtoList = results.getContent().stream()
                .map(DatasetDTO::fromEntity)
                .collect(Collectors.toList());

        PagedResponse<DatasetDTO> pagedResponse = PagedResponse.of(
                dtoList,
                results.getTotalElements(),
                results.getTotalPages(),
                results.getNumber(),
                results.getSize(),
                results.hasNext(),
                results.hasPrevious()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @Operation(
            summary = "Get dataset statistics",
            description = "Returns overall statistics about LMIA datasets including total records, approved/denied counts, and approval rate. Results are cached for 30 minutes."
    )
    @GetMapping("/statistics")
    @Cacheable(value = "statistics", unless = "#result == null || #result.data.totalRecords == 0")
    public ResponseEntity<ApiResponse<Object>> getStatistics() {
        long totalRecords = datasetRepository.count();
        long approvedCount = datasetRepository.findByStatus(Dataset.DecisionStatus.APPROVED, 
                PageRequest.of(0, 1)).getTotalElements();
        long deniedCount = datasetRepository.findByStatus(Dataset.DecisionStatus.DENIED, 
                PageRequest.of(0, 1)).getTotalElements();

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("approvedCount", approvedCount);
        stats.put("deniedCount", deniedCount);
        stats.put("approvalRate", totalRecords > 0 ? (double) approvedCount / totalRecords * 100 : 0);
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(
            summary = "Export datasets to CSV",
            description = "Export filtered datasets to CSV format. All matching records are exported (not paginated)."
    )
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportToCsv(
            @RequestParam(required = false) String employer,
            @RequestParam(required = false) String nocCode,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String status) {
        try {
            Dataset.DecisionStatus decisionStatus = null;
            if (status != null && !status.isEmpty()) {
                try {
                    decisionStatus = Dataset.DecisionStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            }

            byte[] csvData = exportService.exportToCsv(employer, nocCode, province, decisionStatus);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", "lmia_data.csv");
            
            return new ResponseEntity<>(csvData, headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Error exporting to CSV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "Export datasets to Excel",
            description = "Export filtered datasets to Excel (XLSX) format. All matching records are exported (not paginated)."
    )
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportToExcel(
            @RequestParam(required = false) String employer,
            @RequestParam(required = false) String nocCode,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String status) {
        try {
            Dataset.DecisionStatus decisionStatus = null;
            if (status != null && !status.isEmpty()) {
                try {
                    decisionStatus = Dataset.DecisionStatus.valueOf(status.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            }

            byte[] excelData = exportService.exportToExcel(employer, nocCode, province, decisionStatus);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "lmia_data.xlsx");
            
            return new ResponseEntity<>(excelData, headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Error exporting to Excel", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "Get list of provinces",
            description = "Returns a list of all provinces/territories that have data in the database. Results are cached for 1 hour."
    )
    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<String>>> getProvinces() {
        List<String> provinces = referenceDataService.getProvinces();
        return ResponseEntity.ok(ApiResponse.success(provinces));
    }

    @Operation(
            summary = "Get list of NOC codes",
            description = "Returns a list of all NOC codes that have data in the database. Results are cached for 1 hour."
    )
    @GetMapping("/noc-codes")
    public ResponseEntity<ApiResponse<List<String>>> getNocCodes() {
        List<String> nocCodes = referenceDataService.getNocCodes();
        return ResponseEntity.ok(ApiResponse.success(nocCodes));
    }

    @Operation(
            summary = "Get NOC codes with titles",
            description = "Returns a list of NOC codes with their titles. Results are cached for 1 hour."
    )
    @GetMapping("/noc-codes/with-titles")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getNocCodesWithTitles() {
        List<ReferenceDataService.NocCodeInfo> nocCodes = referenceDataService.getNocCodesWithTitles();
        List<Map<String, String>> result = nocCodes.stream()
                .map(info -> Map.of("code", info.getCode(), "title", info.getTitle()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Get province record counts",
            description = "Returns count of records per province. Results are cached for 30 minutes."
    )
    @GetMapping("/provinces/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getProvinceCounts() {
        Map<String, Long> counts = referenceDataService.getProvinceCounts();
        return ResponseEntity.ok(ApiResponse.success(counts));
    }
}

