package dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import model.Dataset;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDTO {
    private Long id;
    private String province;
    private String stream;
    private String employer;
    private String city;
    private String postalCode;
    private String nocCode;
    private String nocTitle;
    private Integer positionsApproved;
    private String status;
    private LocalDate decisionDate;
    private String sourceFile;

    public static DatasetDTO fromEntity(Dataset dataset) {
        return new DatasetDTO(
                dataset.getId(),
                dataset.getProvince(),
                dataset.getStream(),
                dataset.getEmployer(),
                dataset.getCity(),
                dataset.getPostalCode(),
                dataset.getNocCode(),
                dataset.getNocTitle(),
                dataset.getPositionsApproved(),
                dataset.getStatus().name(),
                dataset.getDecisionDate(),
                dataset.getSourceFile()
        );
    }
}

