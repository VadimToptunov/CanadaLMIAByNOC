package config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;

/**
 * Interceptor for logging HTTP requests and responses.
 * Logs request method, URI, headers, and response status.
 */
@Slf4j
public class LoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (log.isDebugEnabled()) {
            logRequest(request);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                @NonNull Object handler, @Nullable Exception ex) {
        if (log.isDebugEnabled()) {
            logResponse(request, response, ex);
        } else if (ex != null || response.getStatus() >= 400) {
            // Always log errors and client/server errors
            logResponse(request, response, ex);
        }
    }

    private void logRequest(HttpServletRequest request) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Incoming Request: ");
        logMessage.append(request.getMethod()).append(" ");
        logMessage.append(request.getRequestURI());
        
        String queryString = request.getQueryString();
        if (queryString != null) {
            logMessage.append("?").append(queryString);
        }
        
        logMessage.append(" | Client: ").append(getClientAddress(request));
        
        if (log.isTraceEnabled()) {
            logMessage.append(" | Headers: ").append(getHeaders(request));
        }
        
        log.debug(logMessage.toString());
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Outgoing Response: ");
        logMessage.append(request.getMethod()).append(" ");
        logMessage.append(request.getRequestURI());
        logMessage.append(" | Status: ").append(response.getStatus());
        logMessage.append(" | Client: ").append(getClientAddress(request));
        
        if (ex != null) {
            logMessage.append(" | Exception: ").append(ex.getClass().getSimpleName());
            log.error(logMessage.toString(), ex);
        } else {
            if (response.getStatus() >= 400) {
                log.warn(logMessage.toString());
            } else {
                log.debug(logMessage.toString());
            }
        }
    }

    private String getClientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getHeaders(HttpServletRequest request) {
        StringBuilder headers = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.append(headerName).append("=").append(request.getHeader(headerName));
            if (headerNames.hasMoreElements()) {
                headers.append(", ");
            }
        }
        return headers.toString();
    }
}

