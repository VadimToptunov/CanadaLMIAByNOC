import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DataFilter {
    public void filterCsvByNoc(String noc, File directory, File newDirectory) throws IOException {
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
                        if (!line.contains("Employers who")
                                || !line.contains("Province/Territory")){
                            listOfLines.add(line);
                        }
                    }
                    listOfLines.stream()
                               .distinct()
                               .collect(Collectors.toList());

                    for (String l : listOfLines) {
                        String employeeOne = getData(l, 1);
                        String employeeTwo = getData(l, 2);
                        listOfData.add(employeeOne);
                        listOfData.add(employeeTwo);
                        listOfData.sort(String::compareTo);
                    }
                }
            }
        }
        listOfData.stream()
                  .sorted()
                  .distinct()
                  .collect(Collectors.toList());

        listOfData.removeIf(lne -> lne.matches("^((.*[Ô,É,È]\\w*|\\w*[Ô,É," +
                "È]|UNIVERSIT‚|MONTR‚AL|QU,BEC)(\\s\\S\\w+)(\\s\\w*))$"));

        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true)))) {

            listOfData.stream().distinct().
                    skip(1)
                      .forEach(l -> {
                          try {
                              String emp = l + "\n";
                              writer.write(emp);
                              log.debug(emp);
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

    public String getData(String line, int number){
        return line.split(",")[number]
                .toUpperCase()
                .replace(".", "")
                .replace("  ", " ")
                .replace("\"", "")
                .replace("INCORPORATED", "INC")
                .replace("LIMITED", "LTD")
                .replaceAll("((HIGH|LOW) WAGE|(GLOBAL " +
                                "TALENT STREAM|GLOBAL TALENT)|PRIMARY AGRICULTURE)",
                        "")
                .replaceAll("\\d{6,8} (CANADA|CANDA|ONTARIO" +
                        "|MANITOBA|ALBERTA|BC) " +
                        "(INC|LTD|CORPORATION|CORP)", "")
                .replaceAll("\\d{6,8} (CANADA|ONTARIO" +
                        "|MANITOBA|ALBERTA|BC)","")
                .replaceAll("(\\d{4}-\\d{4} (QU\\SBEC|QC)" +
                        " (INC|LTD)|\\d{4}-\\d{4}QUEBECINC)", "")
                .replace(" O/A ", "")
                .replace(" (", "")
                .replace("(", "")
                .replace(") ", "")
                .replace(")", "");
    }
}
