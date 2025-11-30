package dataProcessors;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;

@Slf4j
@Component
public class DatasetDownloader {

    Map<String, String> createHeaders() {
        Map<String, String> headers = new TreeMap<>();
        headers.put("User-Agent", String.valueOf(UUID.randomUUID().getLeastSignificantBits()));
        headers.put("Postman-Token", UUID.randomUUID().toString());
        headers.put("Accept", "*/*");
        headers.put("Cache-Control", "no-cache");
        headers.put("Accept-Encoding", "gzip, deflate, br");

        return headers;
    }

    private Response sendRequestAndGetResponse(Map<String, String> headers) {
        baseURI = "https://open.canada.ca";
        Response response = given().when()
                .headers(headers)
                .queryParam("q", "lmia")
                .get("/data/en/api/3/action/package_search");
        log.info("Sent request to get data on LMIA datasets by NOC.");
        log.debug(response.prettyPrint());

        return response;
    }

    private List<String> parseResponseAndExtractLinks(Response response) {
        List<String> urls = new ArrayList<>();
        List<LinkedHashMap<String, Object>> listOfResults = response.jsonPath().get("result" + ".results[0].resources");
        log.debug(String.valueOf(listOfResults));
        if (listOfResults != null) {
            for (LinkedHashMap<String, Object> r : listOfResults) {
                String name = r.get("name").toString();
                String lcase = name.toLowerCase();
                if (lcase.contains("national occupational classification (noc)") && lcase.contains("positive") && lcase.contains("en")) {
                    log.debug(String.format("File name is : %s", name));
                    String urlString = r.get("url").toString();
                    if (!urlString.toLowerCase().contains("_fr") && !urlString.toLowerCase().contains("useb")) {
                        urls.add(urlString);
                    }
                }
            }
        }

        return urls;
    }

    private List<String> getCsvFilesLinks() {
        Map<String, String> headers = createHeaders();
        Response response = sendRequestAndGetResponse(headers);
        return parseResponseAndExtractLinks(response);
    }

    private void writeToFile(byte[] fileContents, File outputFile) {
        try (FileOutputStream outStream = new FileOutputStream(outputFile)) {
            outStream.write(fileContents);
            log.info(String.format("Writing to file %s", outputFile.getAbsolutePath()));
        } catch (Exception e) {
            log.info(String.format("Error writing to file %s", outputFile.getAbsolutePath()), e);
        }
    }

    public void downloadFiles(File outputDirectory) {
        if (!outputDirectory.exists()) {
            boolean created = outputDirectory.mkdirs();
            if (!created) {
                log.error("Failed to create output directory: {}", outputDirectory.getAbsolutePath());
                return;
            }
        }
        
        List<String> urls = getCsvFilesLinks();
        log.info("Found {} files to download", urls.size());
        
        for (String url : urls) {
            try {
                byte[] fileContents = given().when().get(url).asByteArray();
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                // Clean filename from query parameters
                if (fileName.contains("?")) {
                    fileName = fileName.substring(0, fileName.indexOf('?'));
                }
                File outputFile = new File(outputDirectory, fileName);
                
                // Skip if file already exists
                if (outputFile.exists()) {
                    log.debug("File already exists, skipping: {}", fileName);
                    continue;
                }
                
                writeToFile(fileContents, outputFile);
            } catch (Exception e) {
                log.error("Error downloading file from URL {}: {}", url, e.getMessage(), e);
            }
        }
        
        log.info("Download completed. Files saved to: {}", outputDirectory.getAbsolutePath());
    }
}