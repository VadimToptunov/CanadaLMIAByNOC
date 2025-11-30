package org.example;

import config.CacheConfig;
import config.RateLimitConfig;
import config.SecurityConfig;
import controller.AdminController;
import controller.DatasetController;
import dataProcessors.DataParser;
import dataProcessors.DatasetDownloader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import repository.DatasetRepository;
import service.ExportService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AppMainTest {

    @Autowired
    private AppBody appBody;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetController datasetController;

    @Autowired
    private AdminController adminController;

    @Autowired
    private ExportService exportService;

    @Autowired
    private DataParser dataParser;

    @Autowired
    private DatasetDownloader datasetDownloader;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private CacheConfig cacheConfig;

    @Autowired
    private RateLimitConfig rateLimitConfig;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired(required = false)
    private SecurityFilterChain securityFilterChain;

    @Test
    void contextLoads() {
        // Test that Spring context loads successfully
        assertNotNull(appBody, "AppBody service should be loaded");
        assertNotNull(datasetRepository, "DatasetRepository should be loaded");
        assertNotNull(datasetController, "DatasetController should be loaded");
        assertNotNull(adminController, "AdminController should be loaded");
        assertNotNull(exportService, "ExportService should be loaded");
        assertNotNull(dataParser, "DataParser component should be loaded");
        assertNotNull(datasetDownloader, "DatasetDownloader component should be loaded");
    }

    @Test
    void securityConfigurationLoaded() {
        // Test that security configuration is properly loaded
        assertNotNull(securityConfig, "SecurityConfig should be loaded");
        assertNotNull(securityFilterChain, "SecurityFilterChain should be created");
    }

    @Test
    void cacheConfigurationLoaded() {
        // Test that cache configuration is properly loaded
        assertNotNull(cacheConfig, "CacheConfig should be loaded");
        assertNotNull(cacheManager, "CacheManager should be created");
    }

    @Test
    void rateLimitConfigurationLoaded() {
        // Test that rate limit configuration is properly loaded
        assertNotNull(rateLimitConfig, "RateLimitConfig should be loaded");
        // Verify that the configuration can create counters
        assertNotNull(rateLimitConfig.getOrCreateCounter("test-key"), 
                "RateLimitConfig should be able to create request counters");
    }

    @Test
    void appBodyDependenciesInjected() {
        // Test that AppBody has all required dependencies injected
        assertNotNull(appBody, "AppBody should be initialized");
        // AppBody dependencies are injected via constructor, so if context loads, they are present
    }

    @Test
    void repositoryIsAccessible() {
        // Test that repository is accessible and functional
        assertNotNull(datasetRepository, "DatasetRepository should be accessible");
        // Just verify we can call a method without exception
        long count = datasetRepository.count();
        assertTrue(count >= 0, "Count should be non-negative");
    }
}

