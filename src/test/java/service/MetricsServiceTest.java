package service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void testRecordSearch() {
        metricsService.recordSearch("employer");
        verify(meterRegistry, atLeastOnce()).counter(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void testRecordSearchError() {
        metricsService.recordSearchError("employer", "invalid_input");
        verify(meterRegistry, atLeastOnce()).counter(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void testRecordFileDownload() {
        metricsService.recordFileDownload(true);
        metricsService.recordFileDownload(false);
        verify(meterRegistry, atLeast(2)).counter(any(String.class), any(String.class));
    }

    @Test
    void testRecordWebsiteUrlLookup() {
        metricsService.recordWebsiteUrlLookup(true);
        metricsService.recordWebsiteUrlLookup(false);
        verify(meterRegistry, atLeast(2)).counter(any(String.class), any(String.class));
    }

    @Test
    void testTimerOperations() {
        Timer.Sample sample = metricsService.startSearchTimer();
        assert sample != null;
        
        metricsService.stopSearchTimer(sample, "employer");
        verify(meterRegistry, atLeastOnce()).timer(any(String.class), any(String.class), any(String.class));
    }
}

