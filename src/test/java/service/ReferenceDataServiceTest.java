package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.DatasetRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private DatasetRepository datasetRepository;

    private ReferenceDataService referenceDataService;

    @BeforeEach
    void setUp() {
        referenceDataService = new ReferenceDataService(datasetRepository);
    }

    @Test
    void testGetProvinces() {
        // Given
        List<String> expectedProvinces = Arrays.asList("Ontario", "Quebec", "British Columbia");
        when(datasetRepository.findDistinctProvinces()).thenReturn(expectedProvinces);

        // When
        List<String> provinces = referenceDataService.getProvinces();

        // Then
        assertNotNull(provinces);
        assertEquals(3, provinces.size());
        assertEquals("Ontario", provinces.get(0));
        verify(datasetRepository, times(1)).findDistinctProvinces();
    }

    @Test
    void testGetNocCodes() {
        // Given
        List<String> expectedNocCodes = Arrays.asList("0211", "12104", "2171");
        when(datasetRepository.findDistinctNocCodes()).thenReturn(expectedNocCodes);

        // When
        List<String> nocCodes = referenceDataService.getNocCodes();

        // Then
        assertNotNull(nocCodes);
        assertEquals(3, nocCodes.size());
        verify(datasetRepository, times(1)).findDistinctNocCodes();
    }

    @Test
    void testGetNocCodesWithTitles() {
        // Given
        List<Object[]> mockData = Arrays.asList(
                new Object[]{"0211", "Engineering managers"},
                new Object[]{"12104", "Employment insurance officers"}
        );
        when(datasetRepository.findDistinctNocCodesWithTitles()).thenReturn(mockData);

        // When
        List<ReferenceDataService.NocCodeInfo> nocCodes = referenceDataService.getNocCodesWithTitles();

        // Then
        assertNotNull(nocCodes);
        assertEquals(2, nocCodes.size());
        assertEquals("0211", nocCodes.get(0).getCode());
        assertEquals("Engineering managers", nocCodes.get(0).getTitle());
        verify(datasetRepository, times(1)).findDistinctNocCodesWithTitles();
    }

    @Test
    void testGetProvinceCounts() {
        // Given
        List<Object[]> mockData = Arrays.asList(
                new Object[]{"Ontario", 1000L},
                new Object[]{"Quebec", 800L}
        );
        when(datasetRepository.findProvinceCounts()).thenReturn(mockData);

        // When
        Map<String, Long> counts = referenceDataService.getProvinceCounts();

        // Then
        assertNotNull(counts);
        assertEquals(2, counts.size());
        assertEquals(1000L, counts.get("Ontario"));
        assertEquals(800L, counts.get("Quebec"));
        verify(datasetRepository, times(1)).findProvinceCounts();
    }

    @Test
    void testNocCodeInfo() {
        // Given
        ReferenceDataService.NocCodeInfo info = new ReferenceDataService.NocCodeInfo("0211", "Engineering managers");

        // Then
        assertEquals("0211", info.getCode());
        assertEquals("Engineering managers", info.getTitle());
    }
}
