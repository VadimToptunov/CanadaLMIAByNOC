package integration;

import dto.ApiResponse;
import dto.PagedResponse;
import dto.SearchRequest;
import model.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import repository.DatasetRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for dataset search functionality.
 * Tests the complete flow from HTTP request to database query and response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class DatasetSearchIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatasetRepository datasetRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/datasets";
        // Clean up test data if needed
        datasetRepository.deleteAll();
    }

    @Test
    void testSearchDatasets_EmptyDatabase() {
        // When
        SearchRequest request = new SearchRequest();
        request.setPage(0);
        request.setSize(20);
        
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                baseUrl + "/search", request, ApiResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        ApiResponse<?> body = response.getBody();
        assertNotNull(body);
        if (body.getData() != null) {
            @SuppressWarnings("unchecked")
            PagedResponse<Object> pagedResponse = (PagedResponse<Object>) body.getData();
            assertEquals(0, pagedResponse.getTotalElements());
        }
    }

    @Test
    void testSearchDatasets_WithData() {
        // Given
        Dataset dataset = createTestDataset("Test Company", "0211", "Ontario");
        assertNotNull(dataset);
        datasetRepository.save(dataset);

        // When
        SearchRequest request = new SearchRequest();
        request.setEmployer("Test");
        request.setPage(0);
        request.setSize(20);
        
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                baseUrl + "/search", request, ApiResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        ApiResponse<?> body = response.getBody();
        assertNotNull(body);
        if (body.getData() != null) {
            @SuppressWarnings("unchecked")
            PagedResponse<Object> pagedResponse = (PagedResponse<Object>) body.getData();
            assertTrue(pagedResponse.getTotalElements() > 0);
        }
    }

    @Test
    void testSearchByNocCode() {
        // Given
        Dataset dataset = createTestDataset("Test Company", "0211", "Ontario");
        assertNotNull(dataset);
        datasetRepository.save(dataset);

        // When
        @SuppressWarnings("rawtypes")
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                baseUrl + "/noc/0211?page=0&size=20", ApiResponse.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetStatistics() {
        // Given
        Dataset dataset1 = createTestDataset("Company1", "0211", "Ontario");
        Dataset dataset2 = createTestDataset("Company2", "12104", "Quebec");
        assertNotNull(dataset1);
        assertNotNull(dataset2);
        List<Dataset> datasets = List.of(dataset1, dataset2);
        assertNotNull(datasets);
        datasetRepository.saveAll(datasets);

        // When
        ResponseEntity<Object> response = restTemplate.getForEntity(
                baseUrl + "/statistics", Object.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    private Dataset createTestDataset(String employer, String nocCode, String province) {
        Dataset dataset = new Dataset();
        dataset.setEmployer(employer);
        dataset.setNocCode(nocCode);
        dataset.setNocTitle("Test Title");
        dataset.setProvince(province);
        dataset.setStream("High Wage");
        dataset.setCity("Toronto");
        dataset.setPositionsApproved(5);
        dataset.setStatus(Dataset.DecisionStatus.APPROVED);
        dataset.setDecisionDate(LocalDate.now());
        dataset.setSourceFile("test.csv");
        return dataset;
    }
}

