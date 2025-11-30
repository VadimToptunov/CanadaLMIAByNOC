package repository;

import model.Dataset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {
    
    // Search by company name (partial match)
    Page<Dataset> findByEmployerContainingIgnoreCase(String employer, Pageable pageable);
    
    // Search by NOC code
    Page<Dataset> findByNocCode(String nocCode, Pageable pageable);
    
    // Search by province
    Page<Dataset> findByProvince(String province, Pageable pageable);
    
    // Search by decision status
    Page<Dataset> findByStatus(Dataset.DecisionStatus status, Pageable pageable);
    
    // Search by decision date
    Page<Dataset> findByDecisionDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    
    // Complex search - using native query to avoid PostgreSQL type inference issues with LOWER()
    @Query(value = "SELECT * FROM lmia_datasets d WHERE " +
           "(:employer IS NULL OR LOWER(CAST(d.employer AS TEXT)) LIKE LOWER('%' || CAST(:employer AS TEXT) || '%')) AND " +
           "(:nocCode IS NULL OR d.noc_code = :nocCode) AND " +
           "(:province IS NULL OR LOWER(CAST(d.province AS TEXT)) = LOWER(CAST(:province AS TEXT))) AND " +
           "(:status IS NULL OR d.status = CAST(:status AS TEXT)) AND " +
           "(:startDate IS NULL OR d.decision_date >= :startDate) AND " +
           "(:endDate IS NULL OR d.decision_date <= :endDate)",
           countQuery = "SELECT COUNT(*) FROM lmia_datasets d WHERE " +
           "(:employer IS NULL OR LOWER(CAST(d.employer AS TEXT)) LIKE LOWER('%' || CAST(:employer AS TEXT) || '%')) AND " +
           "(:nocCode IS NULL OR d.noc_code = :nocCode) AND " +
           "(:province IS NULL OR LOWER(CAST(d.province AS TEXT)) = LOWER(CAST(:province AS TEXT))) AND " +
           "(:status IS NULL OR d.status = CAST(:status AS TEXT)) AND " +
           "(:startDate IS NULL OR d.decision_date >= :startDate) AND " +
           "(:endDate IS NULL OR d.decision_date <= :endDate)",
           nativeQuery = true)
    Page<Dataset> searchDatasets(
            @Param("employer") String employer,
            @Param("nocCode") String nocCode,
            @Param("province") String province,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
    
    // Statistics by company - using native query to avoid PostgreSQL type inference issues
    @Query(value = "SELECT COUNT(*) FROM lmia_datasets d WHERE LOWER(CAST(d.employer AS TEXT)) LIKE LOWER('%' || CAST(:employer AS TEXT) || '%')",
           nativeQuery = true)
    Long countByEmployer(@Param("employer") String employer);
    
    // Statistics by NOC
    @Query("SELECT d.nocCode, d.nocTitle, COUNT(d) as count FROM Dataset d WHERE d.nocCode = :nocCode GROUP BY d.nocCode, d.nocTitle")
    List<Object[]> getStatisticsByNoc(@Param("nocCode") String nocCode);
    
    // Check for exact duplicate by key fields (employer, NOC code, decision date, source file)
    // Using native query to avoid PostgreSQL type inference issues with LOWER()
    @Query(value = "SELECT COUNT(*) > 0 FROM lmia_datasets d WHERE " +
           "LOWER(CAST(d.employer AS TEXT)) = LOWER(CAST(:employer AS TEXT)) AND " +
           "d.noc_code = :nocCode AND " +
           "d.decision_date = :decisionDate AND " +
           "((:sourceFile IS NULL AND d.source_file IS NULL) OR (:sourceFile IS NOT NULL AND d.source_file = :sourceFile))",
           nativeQuery = true)
    boolean existsByKeyFields(
            @Param("employer") String employer,
            @Param("nocCode") String nocCode,
            @Param("decisionDate") LocalDate decisionDate,
            @Param("sourceFile") String sourceFile);
    
    // Find distinct companies with their website URLs
    // Returns companies that have a website URL set
    @Query(value = "SELECT DISTINCT d.employer, d.website_url FROM lmia_datasets d " +
           "WHERE d.website_url IS NOT NULL AND d.website_url != '' " +
           "AND LOWER(CAST(d.employer AS TEXT)) = LOWER(CAST(:employer AS TEXT)) " +
           "LIMIT 1",
           nativeQuery = true)
    List<Object[]> findCompanyWebsiteUrl(@Param("employer") String employer);
    
    // Find all records for a company that need website URL update
    @Query(value = "SELECT * FROM lmia_datasets d WHERE " +
           "LOWER(CAST(d.employer AS TEXT)) = LOWER(CAST(:employer AS TEXT)) AND " +
           "(d.website_url IS NULL OR d.website_url = '' OR d.website_url LIKE 'https://www.google.com/search%')",
           nativeQuery = true)
    List<Dataset> findRecordsNeedingWebsiteUrl(@Param("employer") String employer);
    
    // Find distinct company names that need website URLs
    @Query(value = "SELECT DISTINCT d.employer FROM lmia_datasets d WHERE " +
           "(d.website_url IS NULL OR d.website_url = '' OR d.website_url LIKE 'https://www.google.com/search%') " +
           "ORDER BY d.employer",
           nativeQuery = true)
    List<String> findCompaniesWithoutWebsiteUrl();
    
    // Optimized queries for reference data (cached)
    @Query(value = "SELECT DISTINCT d.province FROM lmia_datasets d ORDER BY d.province",
           nativeQuery = true)
    List<String> findDistinctProvinces();
    
    @Query(value = "SELECT DISTINCT d.noc_code FROM lmia_datasets d ORDER BY d.noc_code",
           nativeQuery = true)
    List<String> findDistinctNocCodes();
    
    @Query(value = "SELECT DISTINCT d.noc_code, d.noc_title FROM lmia_datasets d " +
           "WHERE d.noc_code IS NOT NULL AND d.noc_title IS NOT NULL " +
           "ORDER BY d.noc_code",
           nativeQuery = true)
    List<Object[]> findDistinctNocCodesWithTitles();
    
    @Query(value = "SELECT d.province, COUNT(*) as count FROM lmia_datasets d " +
           "GROUP BY d.province ORDER BY d.province",
           nativeQuery = true)
    List<Object[]> findProvinceCounts();
}

