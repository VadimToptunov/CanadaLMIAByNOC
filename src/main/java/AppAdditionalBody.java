import java.io.File;
import java.io.IOException;

public class AppAdditionalBody {
    /*
    Use it if you have more or less cleaned and filtered data files with
    lines like: KBS+P CANADA LP,Montreal, QC H8B
    */
    public static void main(String[] args) {
        File outputPath = new File("Downloads/NOC_Result");
        File finalOutputPath = new File("Downloads/CumulativeResult");
        AppBody appBody = new AppBody();
        try {
            appBody.mergeAndFilterFromVariousFilteredDatasets(outputPath,
                    finalOutputPath);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
