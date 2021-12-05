package nocservice.dataProcessors;

import lombok.extern.slf4j.Slf4j;
import nocservice.model.EmployersByNoc;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import nocservice.repository.JdbcEmployersByNocRepository;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class DataFilter {
    private final File directory;
    private final File newDirectory;
    private final String CUMULATEDCSV = "cumulated.csv";

    @Autowired
    JdbcEmployersByNocRepository jdbcEmployersByNocRepository;

    public DataFilter(File directory, File newDirectory){
        this.directory = directory;
        this.newDirectory = newDirectory;
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
        List<ArrayList> listOfData = new ArrayList<>();
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
                    ArrayList tempList = new ArrayList();
                    if ((getData(l).employer != null
                            && !Objects.equals(getData(l).employer, "")
                            && getData(l).employer.length() > 1)
                            && !Objects.equals(getData(l).employer, "(blank)")
                            && (!getData(l).fullNoc.matches("\\d")
                            && getData(l).fullNoc.split("-")[0]
                            .matches("\\d{4}"))){
                        tempList.add(getData(l).employer
                                        .trim()
                                        .replaceAll("\"", "")
                                        .replaceAll("#\\d ", "")
                                        .replaceAll("\\t", ""));
                        tempList.add(getData(l).fullNoc
                                        .trim()
                                        .replaceAll("\"", "")
                                        .split("-")[0]);
                    }
                    listOfData.add(tempList);
                    listOfData.removeIf(element -> element.size() == 0);
                }
                log.debug(String.valueOf(listOfData));
            }
        }

        listOfData.stream()
                .distinct()
                .forEach(l -> {
                    insertIntoDB(l.get(0).toString(), l.get(1).toString());
                });
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

    private void insertIntoDB(String employer, String noc) {

        try {
            jdbcEmployersByNocRepository.save(new EmployersByNoc(employer, noc));
            log.info("Employer line was created successfully.");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
