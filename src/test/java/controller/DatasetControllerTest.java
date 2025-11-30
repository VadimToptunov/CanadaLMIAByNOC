package controller;

import model.Dataset;
import org.example.AppMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import repository.DatasetRepository;
import service.ExportService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AppMain.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null") // Spring test utilities have null-safety warnings but are safe to use
class DatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatasetRepository datasetRepository;

    @MockBean
    private ExportService exportService;

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
    void testSearchDatasets_Success() throws Exception {
        Page<Dataset> page = new PageImpl<>(testDatasets, PageRequest.of(0, 20), 1);
        when(datasetRepository.searchDatasets(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/datasets/search")
                        .param("employer", "Test")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].employer").value("Test Company"));
    }

    @Test
    void testGetStatistics_Success() throws Exception {
        when(datasetRepository.count()).thenReturn(100L);
        Page<Dataset> approvedPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 1), 60);
        Page<Dataset> deniedPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(0, 1), 40);
        
        when(datasetRepository.findByStatus(eq(Dataset.DecisionStatus.APPROVED), any(Pageable.class)))
                .thenReturn(approvedPage);
        when(datasetRepository.findByStatus(eq(Dataset.DecisionStatus.DENIED), any(Pageable.class)))
                .thenReturn(deniedPage);

        mockMvc.perform(get("/api/datasets/statistics")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRecords").value(100));
    }

    @Test
    void testSearchByEmployer_Success() throws Exception {
        Page<Dataset> page = new PageImpl<>(testDatasets, PageRequest.of(0, 20), 1);
        when(datasetRepository.findByEmployerContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/datasets/employer/Test")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void testSearchByNoc_Success() throws Exception {
        Page<Dataset> page = new PageImpl<>(testDatasets, PageRequest.of(0, 20), 1);
        when(datasetRepository.findByNocCode(anyString(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/datasets/noc/0211")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}

