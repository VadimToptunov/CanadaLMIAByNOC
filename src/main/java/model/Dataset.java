package model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "lmia_datasets", indexes = {
    @Index(name = "idx_employer", columnList = "employer"),
    @Index(name = "idx_noc", columnList = "noc_code"),
    @Index(name = "idx_province", columnList = "province"),
    @Index(name = "idx_date", columnList = "decision_date"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String stream;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String employer;

    @Column(length = 200)
    private String city;

    @Column(length = 20)
    private String postalCode;

    @Column(nullable = false, length = 10)
    private String nocCode;

    @Column(columnDefinition = "TEXT")
    private String nocTitle;

    @Column(nullable = false)
    private Integer positionsApproved;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DecisionStatus status;

    @Column(nullable = false)
    private LocalDate decisionDate;

    @Column(length = 50)
    private String sourceFile;

    @Column(length = 500)
    private String websiteUrl;

    public enum DecisionStatus {
        APPROVED, DENIED
    }
}

