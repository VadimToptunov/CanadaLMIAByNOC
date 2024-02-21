package dataProcessors;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;

@Slf4j
public class DatasetDownloader {

    private Map<String, String> createHeaders() {
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
        List<LinkedHashMap> listOfResults = response.jsonPath().get("result" + ".results[0].resources");
        log.debug(String.valueOf(listOfResults));
        for (LinkedHashMap r : listOfResults) {
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
            outputDirectory.mkdirs();
        }
        List<String> urls = getCsvFilesLinks();
        for (String url : urls) {
            byte[] fileContents = given().when().get(url).asByteArray();
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            File outputFile = new File(outputDirectory, fileName);
            writeToFile(fileContents, outputFile);
        }
    }
}