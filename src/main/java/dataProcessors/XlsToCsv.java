package dataProcessors;

import java.io.*;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class XlsToCsv {
    public static void xlsx(File inputFile, String outputFileName) throws IOException, InvalidFormatException {
        XSSFWorkbook input = new XSSFWorkbook(inputFile);
        CSVPrinter output;
        output = new CSVPrinter(new FileWriter(outputFileName), CSVFormat.DEFAULT);

        String tsv = new XSSFExcelExtractor(input).getText();
        String line;
        try (BufferedReader reader =
                     new BufferedReader(new StringReader(tsv))) {
            while ((line = reader.readLine()) != null) {
                String[] record = line.split("\t");
                output.printRecord((Object) record);
            }
        }
    }
}