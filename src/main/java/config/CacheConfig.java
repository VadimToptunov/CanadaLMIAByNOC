package config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @SuppressWarnings("null")
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "statistics", 
                "provinceList", 
                "nocCodeList",
                "nocCodeWithTitles",
                "provinceCounts"
        );
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.MINUTES) // 1 hour for reference data
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .recordStats();
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}

