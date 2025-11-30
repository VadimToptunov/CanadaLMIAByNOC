package service;

import model.Dataset;
import org.example.AppMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import repository.DatasetRepository;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ContextConfiguration(classes = AppMain.class)
@ActiveProfiles("test")
@SuppressWarnings("null") // Spring test utilities have null-safety warnings but are safe to use
class ExportServiceTest {

    @Autowired
    private ExportService exportService;

    @MockBean
    private DatasetRepository datasetRepository;

    private List<Dataset> testDatasets;

    @BeforeEach
    void setUp() {
        testDatasets = new ArrayList<>();
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setProvince("Ontario");
        dataset.setStream("High Wage");
        dataset.setEmployer("Test Company");
        dataset.setCity("Toronto");
        dataset.setNocCode("0211");
        dataset.setNocTitle("Engineering managers");
        dataset.setPositionsApproved(5);
        dataset.setStatus(Dataset.DecisionStatus.APPROVED);
        dataset.setDecisionDate(LocalDate.of(2021, 5, 15));
        testDatasets.add(dataset);
    }

    @Test
    void testExportToCsv_Success() throws IOException {
        Page<Dataset> page = new PageImpl<>(testDatasets, PageRequest.of(0, 1000), 1);
        when(datasetRepository.searchDatasets(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        byte[] result = exportService.exportToCsv(null, null, null, null);

        assertNotNull(result);
        assertTrue(result.length > 0);
        String csvContent = new String(result);
        assertTrue(csvContent.contains("ID"));
        assertTrue(csvContent.contains("Test Company"));
    }

    @Test
    void testExportToExcel_Success() throws IOException {
        Page<Dataset> page = new PageImpl<>(testDatasets, PageRequest.of(0, 1000), 1);
        when(datasetRepository.searchDatasets(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        byte[] result = exportService.exportToExcel(null, null, null, null);

        assertNotNull(result);
        assertTrue(result.length > 0);
        // Excel files start with PK signature (ZIP format)
        assertTrue(result[0] == 0x50 && result[1] == 0x4B);
    }

    @Test
    void testExportToCsv_EmptyResults() throws IOException {
        Page<Dataset> emptyPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 1000), 0);
        when(datasetRepository.searchDatasets(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        byte[] result = exportService.exportToCsv(null, null, null, null);

        assertNotNull(result);
        String csvContent = new String(result);
        assertTrue(csvContent.contains("ID")); // Should have headers
    }
}

