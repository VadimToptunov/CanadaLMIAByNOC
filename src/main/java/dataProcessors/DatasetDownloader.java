package dataProcessors;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;

@Slf4j
public class DatasetDownloader {
    public List<String> getCsvFilesLinks() {
        List<String> urls = new ArrayList<>();
        baseURI = "https://open.canada.ca";
        Map<String, String> headers = new TreeMap<>();
        headers.put("User-Agent", String.valueOf(UUID.randomUUID().getLeastSignificantBits()));
        headers.put("Postman-Token", UUID.randomUUID().toString());
        headers.put("Accept", "*/*");
        headers.put("Cache-Control", "no-cache");
        headers.put("Accept-Encoding", "gzip, deflate, br");

        Response response = given().when()
                                   .headers(headers)
                                   .queryParam("q", "lmia")
                                   .get("/data/en/api/3/action/package_search");
        log.info("Sent request to get data on LMIA datasets by NOC.");
        log.debug(response.prettyPrint());
        List<LinkedHashMap> listOfResults = response.jsonPath().get("result" +
                ".results[0].resources");
        log.debug(String.valueOf(listOfResults));
        for (LinkedHashMap r : listOfResults) {
            String name = r.get("name").toString();
            String lcase = name.toLowerCase();
            if (lcase.contains("national occupational classification (noc)")
                    && lcase.contains("positive") && lcase.contains("en")) {
                log.debug(String.format("File name is : %s", name));
                String urlString = r.get("url").toString();
                if (!urlString.toLowerCase().contains("_fr") && !urlString.toLowerCase().contains("useb")) {
                    urls.add(urlString);
                }
            }
        }
        return urls;
    }

    public void downloadCsvFilesByLink(List<String> urls,
                                        File downloadFolder) {
        log.debug(String.format("Folder to save csv-files: %s", downloadFolder));
        downloadFolder.mkdirs();
        for (String url : urls) {
            String[] splitted = url.split("/");
            String downloadFileName = splitted[splitted.length - 1];
            File checkDownloaded = new File(downloadFolder.getPath(), downloadFileName);
            if (checkDownloaded.exists()) {
                checkDownloaded.delete();
            }

            log.info("Getting csv files from url.");
            downloadUrlAsFile(url, downloadFolder, downloadFileName);
        }
    }

    private void downloadUrlAsFile(final String urlToDownload,
                                   File outputPath,
                                   String filename) {
        File outputFile = new File(outputPath.getPath(), filename);
        final Response response = given().when().get(urlToDownload).andReturn();

        if (response.getStatusCode() == 200) {
            byte[] fileContents = response.getBody().asByteArray();
            log.info("Writing to file from url.");
            writeToFile(fileContents, outputFile);
        }
    }

    private void writeToFile(byte[] fileContents, File outputFile) {
        OutputStream outStream = null;
        try {
            outStream = new FileOutputStream(outputFile);
            outStream.write(fileContents);
        }
        catch (Exception e) {

            log.info(String.format("Error writing to file %s",
                    outputFile.getAbsolutePath()));
            log.debug(e.getMessage());
        }
        finally {

            if (outStream != null) {
                try {
                    log.info(String.format("Closing file %s",
                            outputFile.getAbsolutePath()));
                    outStream.close();
                }
                catch (IOException e) {
                    log.info("Error closing file.");
                    log.debug(e.getMessage());
                }
            }
        }
    }
}