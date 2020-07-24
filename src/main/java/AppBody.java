import dataProcessors.DataFilter;
import dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.util.*;

@Slf4j
public class AppBody {
    private final String DOWNLOADS = "Downloads/";
    private final File OUTPUTPATH = new File(DOWNLOADS + "NOCs");
    private final File FINALOUTPUTPATH = new File(DOWNLOADS + "NOC_Result");
    private final File RESULTPATH = new File(DOWNLOADS + "CumulativeResult");

    public void createAFileByNoc() {
        DatasetDownloader datasetDownloader = new DatasetDownloader();
        DataFilter filter = new DataFilter();
        List<String> csvUrls = datasetDownloader.getCsvFilesLinks();
        datasetDownloader.downloadCsvFilesByLink(csvUrls, OUTPUTPATH);
        String[] nocs = {"2171", "2172", "2173", "2174", "2175", "2281",
                "2283"};
        for (String noc : nocs) {
            try {
                filter.filterCsvByNoc(noc, OUTPUTPATH, FINALOUTPUTPATH);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            filter.mergeAndFilterFromVariousFilteredDatasets(FINALOUTPUTPATH,
                    RESULTPATH);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        cleanUp(OUTPUTPATH);
        cleanUp(FINALOUTPUTPATH);
    }

    private void cleanUp(File directory) {
        for (File f : directory.listFiles()) {
            f.delete();
        }
        directory.delete();
    }
}
