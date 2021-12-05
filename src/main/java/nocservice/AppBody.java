package nocservice;

import nocservice.dataProcessors.DataFilter;
import nocservice.dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.util.*;

@Slf4j
public class AppBody {
    private final java.lang.String DOWNLOADS = "Downloads/";
    private final File OUTPUTPATH = new File(DOWNLOADS + "NOCs");
    private final File FINALOUTPUTPATH = new File(DOWNLOADS + "NOC_Result");

    public void createAFileByNoc() {
        DatasetDownloader datasetDownloader = new DatasetDownloader();
        DataFilter filter = new DataFilter(OUTPUTPATH, FINALOUTPUTPATH);
        List<String> csvUrls = datasetDownloader.getCsvFilesLinks();
        datasetDownloader.downloadCsvFilesByLink(csvUrls, OUTPUTPATH);
        File cumulatedCsv = null;
        try {
            cumulatedCsv = filter.filterCsvByNoc();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert cumulatedCsv != null;
            filter.cleanUpCumulated(cumulatedCsv);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        cleanUp(OUTPUTPATH);
        cleanUp(FINALOUTPUTPATH);
    }

    private void cleanUp(File directory) {
        for (File f : Objects.requireNonNull(directory.listFiles())) {
            f.delete();
        }
        directory.delete();
    }
}
