import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AppBody {
    public void createAFileByNoc(String noc) {
        File outputPath = new File("Downloads/NOCs");
        File finalOutputPath = new File("Downloads/NOC_Result");
        List<String> csvUrls = getCsvFilesLinks();
        downloadCsvFilesByLink(csvUrls, noc, outputPath);
        try {
            filterCsvByNoc(noc, outputPath, finalOutputPath);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        cleanUp(outputPath);
    }


    private List<String> getCsvFilesLinks() {
        List<String> urls = new ArrayList<>();
        baseURI = "https://open.canada.ca";
        Map<String, String> headers = new TreeMap<>();
        headers.put("User-Agent", "PostmanRuntime/7.26.1");
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
            if (name.contains(
                    "National Occupational Classification (NOC)") &&
                    (name.contains("Positive") ||
                            name.contains("positive"))) {
                log.debug(String.format("File name is : %s", name));
                String urlString = r.get("url").toString();
                if (urlString.contains("EN")) {
                    log.debug(String.format("CSV file to download: %s",
                            urlString));
                    urls.add(urlString);
                }
            }
        }
        return urls;
    }

    private void downloadCsvFilesByLink(List<String> urls, String noc,
                                        File downloadFolder) {
        log.debug(String.format("Folder to save csv-files: %s", downloadFolder));
        downloadFolder.mkdirs();
        for (String url : urls) {
            String downloadFileName = String.format("noc_%s_%s.csv", noc,
                    UUID.randomUUID());
            File checkDownloaded = new File(downloadFolder.getPath(), downloadFileName);
            if (checkDownloaded.exists()) {
                checkDownloaded.delete();
            }

            log.info("Getting csv files from url.");
            downloadUrlAsFile(url, downloadFolder, downloadFileName);
        }
    }

    private void filterCsvByNoc(String noc, File directory, File newDirectory) throws IOException {
        String line;
        String outputFileName = String.format("noc_%s_result.csv",
                noc);
        newDirectory.mkdir();

        File file = new File(newDirectory.getPath(),
                outputFileName);
        file.createNewFile();
        log.info(String.format("%s is created.", outputFileName));

        for (File csvFile : directory.listFiles()) {
            if (csvFile.isFile()) {
                try (BufferedReader br =
                             new BufferedReader(new FileReader(csvFile))) {

                    while ((line = br.readLine()) != null) {
                        if (line.contains(String.format("%s-", noc))) {
                            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                                    new FileOutputStream(file, true)))) {
                                log.debug(line);
                                writer.write(line + "\n");
                            }
                        }
                    }
                }
                catch (IOException e) {
                    log.info("Error writing info.");
                    log.debug(e.getMessage());
                }
            }
        }
    }

    private void cleanUp(File directory) {
        for (File f : directory.listFiles()) {
            f.delete();
        }
        directory.delete();
    }

    private void downloadUrlAsFile(final String urlToDownload,
                                   File outputPath,
                                   String filename) {
        File outputFile = new File(outputPath.getPath(), filename);
        final Response response = given().when().get(urlToDownload).andReturn();

        if (response.getStatusCode() == 200) {

            if (outputFile.exists()) {
                outputFile.delete();
            }
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

    public void mergeAndFilterFromVariousFilteredDatasets(File directory,
                                                          File newDirectory)
            throws IOException {
        String line;
        List<String> listOfLines = new ArrayList<>();
        List<String> listOfData = new ArrayList<>();
        String outputFileName = "result.csv";
        newDirectory.mkdir();

        File file = new File(newDirectory.getPath(),
                outputFileName);
        file.createNewFile();
        log.info(String.format("%s is created.", outputFileName));

        for (File csvFile : directory.listFiles()) {
            if (csvFile.isFile()) {
                try (BufferedReader br =
                             new BufferedReader(new FileReader(csvFile))) {

                    while ((line = br.readLine()) != null) {
                        listOfLines.add(line);
                    }
                    listOfLines.stream()
                               .distinct()
                               .sorted(String::compareTo)
                               .collect(Collectors.toList());
                    for (String l : listOfLines) {
                        if (!l.contains(" QC ") || !l.contains("MONTREAL")
                                || !l.contains("Montreal") || !l.contains("Montr�al")
                                || !l.contains("MONTR�AL") || !l.contains("Qu�bec")
                                || !l.contains("QU�BEC")) {
//                            String neededData = l.split(",")[0];
                            listOfData.add(l.split(",")[0]);
                        }
                    }

                    listOfData.sort(String::compareTo);
                    listOfData.stream()
                              .distinct()
                              .collect(Collectors.toList());
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(file, true)))) {
                        listOfData.stream()
                                  .map(String::toUpperCase)
                                  .distinct()
                                  .forEach(l -> {
                            try {
                                writer.write(l + "\n");
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                    catch (IOException e) {
                        log.info("Error writing info.");
                        log.debug(e.getMessage());
                    }
                }
            }
        }
    }
}
