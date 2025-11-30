package controller;

import dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.AppBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Admin Operations", description = "Administrative endpoints for managing datasets. Requires ADMIN role.")
@SecurityRequirement(name = "basicAuth")
public class AdminController {

    private final AppBody appBody;

    @Operation(
            summary = "Download and process datasets",
            description = "Downloads LMIA datasets from open.canada.ca and processes them into the database. This operation may take several minutes."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Download started successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Admin authentication required"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error during download"
            )
    })
    @PostMapping("/download")
    public ResponseEntity<ApiResponse<Object>> downloadDatasets() {
        try {
            appBody.downloadDatasets();
            Map<String, Object> data = new HashMap<>();
            data.put("message", "Datasets download and processing started");
            return ResponseEntity.ok(ApiResponse.success("Download started successfully", data));
        } catch (Exception e) {
            log.error("Error downloading datasets", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to download datasets: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Process existing dataset files",
            description = "Processes existing dataset files from the savedDatasets directory and saves them to the database. Skips duplicate records."
    )
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<Object>> processDatasets() {
        try {
            appBody.processAndSaveDatasets();
            Map<String, Object> data = new HashMap<>();
            data.put("message", "Datasets processing completed");
            data.put("totalRecords", appBody.getTotalRecordsCount());
            return ResponseEntity.ok(ApiResponse.success("Processing completed successfully", data));
        } catch (Exception e) {
            log.error("Error processing datasets", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to process datasets: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get system statistics",
            description = "Returns system-level statistics including total number of records in the database."
    )
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", appBody.getTotalRecordsCount());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}

