package dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class SearchRequest {
    @Size(max = 500, message = "Employer name must not exceed 500 characters")
    private String employer;

    @Size(max = 10, message = "NOC code must not exceed 10 characters")
    private String nocCode;

    @Size(max = 100, message = "Province name must not exceed 100 characters")
    private String province;

    private String status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Min(value = 0, message = "Page number must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "Page size must be >= 1")
    private int size = 20;
}

