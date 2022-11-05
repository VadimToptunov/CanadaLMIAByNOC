import dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.util.*;

@Slf4j
public class AppBody {
    private final java.lang.String DOWNLOADS = "Downloads/";
    private final File OUTPUTPATH = new File(DOWNLOADS + "NOCs");

    public void createAFileByNoc() {
        DatasetDownloader datasetDownloader = new DatasetDownloader();
        List<String> csvUrls = datasetDownloader.getCsvFilesLinks();
        datasetDownloader.downloadCsvFilesByLink(csvUrls, OUTPUTPATH);
        //TODO: Read files, cleanup them from excess data and save to the DB.
//        cleanUp(OUTPUTPATH);
    }

    private void cleanUp(File directory) {
        for (File f : Objects.requireNonNull(directory.listFiles())) {
            f.delete();
        }
        directory.delete();
    }
}
