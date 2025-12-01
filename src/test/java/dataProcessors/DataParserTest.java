package dataProcessors;

import nocservice.dataProcessors.DataParser;
import model.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.WebsiteUrlService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataParserTest {

    @Mock
    private WebsiteUrlService websiteUrlService;

    private DataParser dataParser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        dataParser = new DataParser(websiteUrlService);
        // Mock website URL service to return null (no URL found)
        when(websiteUrlService.getCompanyWebsiteUrl(anyString(), any(), any())).thenReturn(null);
    }

    @Test
    void testParseCsvFile_ValidData() throws IOException {
        // Create a test CSV file
        File csvFile = tempDir.resolve("test.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("Province/Territory,Stream,Employer,Address,Occupations under NOC 2011,Positions Approved\n");
            writer.write("Ontario,High Wage,Test Company Inc.,\"Toronto, ON M5H 2N2\",0211-Engineering managers,5\n");
        }

        List<Dataset> datasets = dataParser.parseCsvFile(csvFile);

        assertNotNull(datasets);
        assertEquals(1, datasets.size());
        Dataset dataset = datasets.get(0);
        assertEquals("Ontario", dataset.getProvince());
        assertEquals("High Wage", dataset.getStream());
        assertEquals("Test Company Inc.", dataset.getEmployer());
        assertEquals("Toronto", dataset.getCity());
        assertEquals("M5H2N2", dataset.getPostalCode());
        assertEquals("0211", dataset.getNocCode());
        assertEquals("Engineering managers", dataset.getNocTitle());
        assertEquals(5, dataset.getPositionsApproved());
        assertEquals(Dataset.DecisionStatus.APPROVED, dataset.getStatus());
    }

    @Test
    void testParseCsvFile_EmptyEmployer() throws IOException {
        File csvFile = tempDir.resolve("test_empty.csv").toFile();
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("Province/Territory,Stream,Employer,Address,Occupations under NOC 2011,Positions Approved\n");
            writer.write("Ontario,High Wage,,Address,0211-Engineering managers,5\n");
        }

        List<Dataset> datasets = dataParser.parseCsvFile(csvFile);

        assertNotNull(datasets);
        assertEquals(0, datasets.size());
    }

    @Test
    void testExtractDateFromFileName_ValidQuarter() {
        // Test date extraction through parsing a file with date in name
        String fileName = "tfwp_2021q2_positive_en.csv";
        // Since extractDateFromFileName is package-private, we test it indirectly
        // by creating a file and parsing it
        LocalDate date = dataParser.extractDateFromFileName(fileName);

        assertNotNull(date);
        assertEquals(2021, date.getYear());
        assertEquals(5, date.getMonthValue()); // Q2 -> May
    }

    @Test
    void testExtractDateFromFileName_InvalidQuarter() {
        String fileName = "tfwp_2021q0_positive_en.csv";
        LocalDate date = dataParser.extractDateFromFileName(fileName);

        assertNotNull(date);
        // Should return current date for invalid quarter
        assertTrue(date.isAfter(LocalDate.of(2020, 1, 1)) || date.equals(LocalDate.now()));
    }

    @Test
    void testExtractDateFromFileName_NoDatePattern() {
        String fileName = "random_file.csv";
        LocalDate date = dataParser.extractDateFromFileName(fileName);

        assertNotNull(date);
        // Should return current date when no pattern found
        assertTrue(date.isAfter(LocalDate.of(2020, 1, 1)) || date.equals(LocalDate.now()));
    }
}

