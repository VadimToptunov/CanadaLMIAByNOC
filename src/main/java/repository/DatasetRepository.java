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
    
    // Complex search
    @Query("SELECT d FROM Dataset d WHERE " +
           "(:employer IS NULL OR LOWER(d.employer) LIKE LOWER(CONCAT('%', :employer, '%'))) AND " +
           "(:nocCode IS NULL OR d.nocCode = :nocCode) AND " +
           "(:province IS NULL OR d.province = :province) AND " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:startDate IS NULL OR d.decisionDate >= :startDate) AND " +
           "(:endDate IS NULL OR d.decisionDate <= :endDate)")
    Page<Dataset> searchDatasets(
            @Param("employer") String employer,
            @Param("nocCode") String nocCode,
            @Param("province") String province,
            @Param("status") Dataset.DecisionStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
    
    // Statistics by company
    @Query("SELECT COUNT(d) FROM Dataset d WHERE LOWER(d.employer) LIKE LOWER(CONCAT('%', :employer, '%'))")
    Long countByEmployer(@Param("employer") String employer);
    
    // Statistics by NOC
    @Query("SELECT d.nocCode, d.nocTitle, COUNT(d) as count FROM Dataset d WHERE d.nocCode = :nocCode GROUP BY d.nocCode, d.nocTitle")
    List<Object[]> getStatisticsByNoc(@Param("nocCode") String nocCode);
}

