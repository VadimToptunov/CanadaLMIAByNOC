package controller;

import org.example.AppBody;
import org.example.AppMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AppMain.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null") // Spring Security test utilities have null-safety warnings but are safe to use
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppBody appBody;

    @BeforeEach
    void setUp() {
        when(appBody.getTotalRecordsCount()).thenReturn(100L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDownloadDatasets_Success() throws Exception {
        doNothing().when(appBody).downloadDatasets();

        mockMvc.perform(post("/api/admin/download")
                        .with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Download started successfully"));

        verify(appBody, times(1)).downloadDatasets();
    }

    @Test
    void testDownloadDatasets_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/download")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDownloadDatasets_Error() throws Exception {
        doThrow(new RuntimeException("Download failed")).when(appBody).downloadDatasets();

        mockMvc.perform(post("/api/admin/download")
                        .with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));

        verify(appBody, times(1)).downloadDatasets();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testProcessDatasets_Success() throws Exception {
        doNothing().when(appBody).processAndSaveDatasets();

        mockMvc.perform(post("/api/admin/process")
                        .with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Processing completed successfully"))
                .andExpect(jsonPath("$.data.totalRecords").value(100));

        verify(appBody, times(1)).processAndSaveDatasets();
        verify(appBody, times(1)).getTotalRecordsCount();
    }

    @Test
    void testProcessDatasets_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/process")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testProcessDatasets_Error() throws Exception {
        doThrow(new RuntimeException("Processing failed")).when(appBody).processAndSaveDatasets();

        mockMvc.perform(post("/api/admin/process")
                        .with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));

        verify(appBody, times(1)).processAndSaveDatasets();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetStats_Success() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRecords").value(100));

        verify(appBody, times(1)).getTotalRecordsCount();
    }

    @Test
    void testGetStats_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isUnauthorized());
    }
}

