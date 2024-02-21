import dataProcessors.DatasetDownloader;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.util.*;

@Slf4j
public class AppBody {
    private static final File OUTPUT_DIRECTORY = new File("savedDatasets/NOCs/");

    public void downloadDatasets() {
        DatasetDownloader datasetDownloader = new DatasetDownloader();
        datasetDownloader.downloadFiles(OUTPUT_DIRECTORY);
        //TODO: Read files, cleanup them from excess data and save to the DB.
//        cleanUp(OUTPUTPATH);
    }

    private void cleanUpDirectory(File directory) {
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            file.delete();
        }
        directory.delete();
    }
}
