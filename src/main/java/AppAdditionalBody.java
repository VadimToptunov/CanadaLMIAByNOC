import java.io.File;
import java.io.IOException;

public class AppAdditionalBody {
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
