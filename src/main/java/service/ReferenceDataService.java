package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import repository.DatasetRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for retrieving reference data (provinces, NOC codes, etc.)
 * with caching for improved performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private final DatasetRepository datasetRepository;

    /**
     * Gets list of all provinces/territories that have data in the database.
     * Results are cached for 1 hour.
     * Uses optimized native query instead of loading all records.
     */
    @Cacheable(value = "provinceList", unless = "#result == null || #result.isEmpty()")
    public List<String> getProvinces() {
        log.debug("Fetching provinces from database (not cached)");
        return datasetRepository.findDistinctProvinces();
    }

    /**
     * Gets list of all NOC codes that have data in the database.
     * Results are cached for 1 hour.
     * Uses optimized native query instead of loading all records.
     */
    @Cacheable(value = "nocCodeList", unless = "#result == null || #result.isEmpty()")
    public List<String> getNocCodes() {
        log.debug("Fetching NOC codes from database (not cached)");
        return datasetRepository.findDistinctNocCodes();
    }

    /**
     * Gets list of distinct NOC codes with their titles.
     * Results are cached for 1 hour.
     * Uses optimized native query instead of loading all records.
     */
    @Cacheable(value = "nocCodeWithTitles", unless = "#result == null || #result.isEmpty()")
    public List<NocCodeInfo> getNocCodesWithTitles() {
        log.debug("Fetching NOC codes with titles from database (not cached)");
        return datasetRepository.findDistinctNocCodesWithTitles().stream()
                .map(row -> new NocCodeInfo(
                        (String) row[0],
                        (String) row[1]
                ))
                .collect(Collectors.toList());
    }

    /**
     * Gets count of records by province.
     * Results are cached for 30 minutes.
     * Uses optimized native query with GROUP BY instead of loading all records.
     */
    @Cacheable(value = "provinceCounts", unless = "#result == null || #result.isEmpty()")
    public java.util.Map<String, Long> getProvinceCounts() {
        log.debug("Fetching province counts from database (not cached)");
        return datasetRepository.findProvinceCounts().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    /**
     * Data class for NOC code information.
     */
    public static class NocCodeInfo {
        private final String code;
        private final String title;

        public NocCodeInfo(String code, String title) {
            this.code = code;
            this.title = title;
        }

        public String getCode() {
            return code;
        }

        public String getTitle() {
            return title;
        }
    }
}

