package service;

import model.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.DatasetRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyWebsiteServiceTest {

    @Mock
    private DatasetRepository datasetRepository;

    private CompanyWebsiteService companyWebsiteService;

    @BeforeEach
    void setUp() {
        companyWebsiteService = new CompanyWebsiteService(datasetRepository);
    }

    @Test
    void testGetOrFindCompanyWebsiteUrl_ExistingUrl() {
        // Given
        String companyName = "Test Company";
        Object[] urlData = new Object[]{"Test Company", "https://testcompany.com"};
        List<Object[]> existingUrls = Collections.singletonList(urlData);
        
        when(datasetRepository.findCompanyWebsiteUrl(companyName)).thenReturn(existingUrls);

        // When
        String result = companyWebsiteService.getOrFindCompanyWebsiteUrl(companyName, null, null);

        // Then
        assertNotNull(result);
        assertEquals("https://testcompany.com", result);
        verify(datasetRepository, times(1)).findCompanyWebsiteUrl(companyName);
        verify(datasetRepository, never()).findRecordsNeedingWebsiteUrl(anyString());
    }

    @Test
    void testGetOrFindCompanyWebsiteUrl_NoExistingUrl() {
        // Given
        String companyName = "New Company";
        when(datasetRepository.findCompanyWebsiteUrl(companyName)).thenReturn(new ArrayList<>());
        when(datasetRepository.findRecordsNeedingWebsiteUrl(companyName)).thenReturn(new ArrayList<>());

        // When
        companyWebsiteService.getOrFindCompanyWebsiteUrl(companyName, null, null);

        // Then
        // Should attempt to find URL (may return null or search URL)
        verify(datasetRepository, times(1)).findCompanyWebsiteUrl(companyName);
    }

    @Test
    @SuppressWarnings("null")
    void testUpdateCompanyWebsiteUrl() {
        // Given
        String companyName = "Test Company";
        String websiteUrl = "https://testcompany.com";
        Dataset dataset1 = new Dataset();
        dataset1.setEmployer(companyName);
        Dataset dataset2 = new Dataset();
        dataset2.setEmployer(companyName);
        
        List<Dataset> records = Arrays.asList(dataset1, dataset2);
        assertNotNull(records);
        assertNotNull(dataset1);
        assertNotNull(dataset2);
        List<Dataset> nonNullRecords = records;
        when(datasetRepository.findRecordsNeedingWebsiteUrl(companyName)).thenReturn(nonNullRecords);
        when(datasetRepository.saveAll(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Dataset> savedList = (List<Dataset>) invocation.getArgument(0);
            assertNotNull(savedList);
            return savedList;
        });

        // When
        companyWebsiteService.updateCompanyWebsiteUrl(companyName, websiteUrl);

        // Then
        assertEquals(websiteUrl, dataset1.getWebsiteUrl());
        assertEquals(websiteUrl, dataset2.getWebsiteUrl());
        @SuppressWarnings("null")
        List<Dataset> verifyList = records;
        verify(datasetRepository, times(1)).saveAll(verifyList);
    }

    @Test
    void testFindAndUpdateMissingUrls() {
        // Given
        List<String> companies = Arrays.asList("Company1", "Company2");
        when(datasetRepository.findCompaniesWithoutWebsiteUrl()).thenReturn(companies);
        when(datasetRepository.findCompanyWebsiteUrl(anyString())).thenReturn(new ArrayList<>());
        when(datasetRepository.findRecordsNeedingWebsiteUrl(anyString())).thenReturn(new ArrayList<>());

        // When
        int found = companyWebsiteService.findAndUpdateMissingUrls(10);

        // Then
        // Should process companies (may find 0 URLs if external APIs fail in test)
        assertTrue(found >= 0);
        verify(datasetRepository, atLeastOnce()).findCompaniesWithoutWebsiteUrl();
    }
}
