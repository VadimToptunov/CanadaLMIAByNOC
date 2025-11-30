package config;

import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long TIME_WINDOW_MS = 60_000; // 1 minute

    private final ConcurrentHashMap<String, RequestCounter> requestCounters = new ConcurrentHashMap<>();

    @Bean
    @NonNull
    public RateLimitInterceptor rateLimitInterceptor() {
        return new RateLimitInterceptor(this);
    }

    @Bean
    @NonNull
    public LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Add logging interceptor first (executes first)
        registry.addInterceptor(loggingInterceptor())
                .addPathPatterns("/api/**");
        
        // Add rate limiting interceptor
        registry.addInterceptor(rateLimitInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/datasets/statistics");
    }

    @Getter
    public static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long resetTime = System.currentTimeMillis() + TIME_WINDOW_MS;

        /**
         * Checks and resets the time window if it has expired.
         * This must be called before checking the limit to ensure the window resets
         * even when requests are rejected.
         */
        private void checkAndResetWindow() {
            long now = System.currentTimeMillis();
            if (now > resetTime) {
                count.set(0);
                resetTime = now + TIME_WINDOW_MS;
            }
        }

        public int incrementAndGet() {
            checkAndResetWindow();
            return count.incrementAndGet();
        }

        public boolean isLimitExceeded() {
            // Check and reset window first to ensure expired windows are reset
            // even when requests are rejected (not calling incrementAndGet)
            checkAndResetWindow();
            return count.get() >= MAX_REQUESTS_PER_MINUTE;
        }

        /**
         * Atomically checks if limit is exceeded and increments if not.
         * Returns true if the request should be allowed, false if limit exceeded.
         * This method ensures thread-safety by performing check and increment in a single atomic operation.
         */
        public boolean tryIncrement() {
            checkAndResetWindow();
            // Atomically check and increment: if current value is less than limit, increment and return true
            // Otherwise, return false without incrementing
            int current;
            do {
                current = count.get();
                if (current >= MAX_REQUESTS_PER_MINUTE) {
                    return false;
                }
            } while (!count.compareAndSet(current, current + 1));
            return true;
        }
    }

    public RequestCounter getOrCreateCounter(String key) {
        return requestCounters.computeIfAbsent(key, k -> new RequestCounter());
    }
}

