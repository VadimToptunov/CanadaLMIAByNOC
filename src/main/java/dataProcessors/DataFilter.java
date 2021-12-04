package dataProcessors;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class DataFilter {
    private final File directory;
    private final File newDirectory;
    private final File outputDirectory;
    private final String RESULTCSV = "result.csv";
    private final String CUMULATEDCSV = "cumulated.csv";

    public DataFilter(File directory, File newDirectory, File outputDirectory){
        this.directory = directory;
        this.newDirectory = newDirectory;
        this.outputDirectory = outputDirectory;
    }

    public File filterCsvByNoc() throws IOException {
        String line;
        newDirectory.mkdir();

        File file = new File(newDirectory.getPath(),
                CUMULATEDCSV);
        file.createNewFile();
        log.info(String.format("%s is created.", CUMULATEDCSV));
        for (File fileItem : Objects.requireNonNull(directory.listFiles())) {
            if (fileItem.getName().contains("xlsx")){
                try {
                    XlsToCsv.xlsx(fileItem, fileItem.toString().replace("xlsx", "csv"));
                    fileItem.delete();
                } catch (InvalidFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        for (File csvFile : Objects.requireNonNull(directory.listFiles())) {
            if (csvFile.isFile()) {
                    try (BufferedReader br =
                                 new BufferedReader(new FileReader(csvFile))) {

                        while ((line = br.readLine()) != null) {
                            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                                    new FileOutputStream(file, true)))) {
                                log.debug(line);
                                writer.write(line + "\n");
                            }
                        }
                    } catch (IOException e) {
                        log.info("Error writing info.");
                        log.debug(e.getMessage());
                    }
            }
        }
        return file;
    }

    public void cleanUpCumulated(File cumulatedCsv) throws IOException {
        String line;
        List<String> listOfLines = new ArrayList<>();
        List<String> listOfData = new ArrayList<>();
        outputDirectory.mkdir();

        File file = new File(outputDirectory.getPath(),
                RESULTCSV);
        file.createNewFile();
        log.info(String.format("%s is created.", RESULTCSV));
        if (cumulatedCsv.isFile()) {
            try (BufferedReader br =
                         new BufferedReader(new FileReader(cumulatedCsv))) {

                while ((line = br.readLine()) != null) {
                    if (!line.contains("Employers who")
                            || !line.contains("Province/Territory")){
                        listOfLines.add(line);
                    }
                }
                listOfLines.stream()
                        .skip(2)
                        .distinct()
                        .collect(Collectors.toList());

                for (String l : listOfLines) {
                    if ((getData(l).employer != null
                            && !Objects.equals(getData(l).employer, "")
                            && getData(l).employer.length() > 1)
                            && !Objects.equals(getData(l).employer, "(blank)")
                            && (!getData(l).fullNoc.matches("\\d")
                            && getData(l).fullNoc.split("-")[0]
                            .matches("\\d{4}"))){
                        listOfData.add(String.format("%s, %s"
                                        .replaceAll("[\"\\t \\n]", ""),
                                getData(l).employer
                                        .trim()
                                        .replaceAll("\"", "")
                                        .replaceAll("#\\d ", "")
                                        .replaceAll("\\t", ""),
                                getData(l).fullNoc
                                        .trim()
                                        .replaceAll("\"", "")
                                        .split("-")[0]
                        ));
                    }
                    listOfData.sort(String::compareTo);
                }
                log.debug(String.valueOf(listOfData));
            }
        }
        listOfData.stream()
                .sorted()
                  .distinct()
                  .collect(Collectors.toList());

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(file, true)))) {

        listOfData.stream().distinct().
                forEach(l -> {
                    try {
                        writer.write(l + "\n");
                        log.debug(l + "\n");
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


    private CsvData getData(String line){
        String[] splittedArray = line.split(",");
        if (splittedArray.length >= 6){
            String province = splittedArray[0];
            String program = splittedArray[1];
            String employer = splittedArray[2];
            String city = splittedArray[3];
            String postalCode = splittedArray[4];
            String fullNoc = splittedArray[5];
            return new CsvData(province, program, employer, city, postalCode, fullNoc);
        }
        return new CsvData("", "", "", "", "", "");
    }
}
