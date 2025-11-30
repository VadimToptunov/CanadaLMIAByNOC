package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebsiteUrlServiceTest {

    @Mock
    private CompanyWebsiteService companyWebsiteService;

    private WebsiteUrlService websiteUrlService;

    @BeforeEach
    void setUp() {
        websiteUrlService = new WebsiteUrlService(companyWebsiteService);
    }

    @Test
    void testGetCompanyWebsiteUrl_WithRealUrl() {
        // Given
        String companyName = "Test Company";
        String city = "Toronto";
        String province = "Ontario";
        String realUrl = "https://testcompany.com";
        
        when(companyWebsiteService.getOrFindCompanyWebsiteUrl(companyName, city, province))
                .thenReturn(realUrl);

        // When
        String result = websiteUrlService.getCompanyWebsiteUrl(companyName, city, province);

        // Then
        assertNotNull(result);
        assertEquals(realUrl, result);
        assertFalse(result.contains("google.com/search"));
        verify(companyWebsiteService, times(1)).getOrFindCompanyWebsiteUrl(companyName, city, province);
    }

    @Test
    void testGetCompanyWebsiteUrl_WithSearchUrl() {
        // Given
        String companyName = "Unknown Company";
        String city = "Toronto";
        String province = "Ontario";
        String searchUrl = "https://www.google.com/search?q=Unknown+Company";
        
        when(companyWebsiteService.getOrFindCompanyWebsiteUrl(companyName, city, province))
                .thenReturn(searchUrl);

        // When
        String result = websiteUrlService.getCompanyWebsiteUrl(companyName, city, province);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("google.com/search"));
        verify(companyWebsiteService, times(1)).getOrFindCompanyWebsiteUrl(companyName, city, province);
    }

    @Test
    void testGetCompanyWebsiteUrl_NullCompanyName() {
        // When
        String result = websiteUrlService.getCompanyWebsiteUrl(null, "Toronto", "Ontario");

        // Then
        assertNull(result);
        verify(companyWebsiteService, never()).getOrFindCompanyWebsiteUrl(any(), any(), any());
    }

    @Test
    void testGetCompanyWebsiteUrl_EmptyCompanyName() {
        // When
        String result = websiteUrlService.getCompanyWebsiteUrl("", "Toronto", "Ontario");

        // Then
        assertNull(result);
        verify(companyWebsiteService, never()).getOrFindCompanyWebsiteUrl(any(), any(), any());
    }

    @Test
    void testGetCompanyWebsiteUrl_CompanyNameOnly() {
        // Given
        String companyName = "Test Company";
        String realUrl = "https://testcompany.com";
        
        when(companyWebsiteService.getOrFindCompanyWebsiteUrl(companyName, null, null))
                .thenReturn(realUrl);

        // When
        String result = websiteUrlService.getCompanyWebsiteUrl(companyName);

        // Then
        assertNotNull(result);
        assertEquals(realUrl, result);
        verify(companyWebsiteService, times(1)).getOrFindCompanyWebsiteUrl(companyName, null, null);
    }
}
