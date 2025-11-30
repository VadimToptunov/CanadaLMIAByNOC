# Monitoring Setup Guide

This guide explains how to set up Prometheus, Grafana, and alerting for the LMIA Portal application.

## 📋 Prerequisites

- Docker and Docker Compose installed
- Application running (via `docker-compose.yml`)
- Ports available: 9090 (Prometheus), 3000 (Grafana)

## 🚀 Quick Start

### 1. Start Monitoring Stack

```bash
# Start the main application first (if not already running)
docker-compose up -d

# Start monitoring stack
docker-compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d
```

### 2. Access Services

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000
  - Default credentials: `admin` / `admin` (change on first login)

### 3. Verify Setup

1. Check Prometheus targets: http://localhost:9090/targets
   - Should show `lmia-portal` target as UP

2. Check Grafana datasource: http://localhost:3000/datasources
   - Should show Prometheus datasource configured

3. Import dashboard: http://localhost:3000/dashboards
   - Dashboard should be auto-imported from `grafana/dashboards/`

## 📊 Prometheus Configuration

### Configuration File: `prometheus.yml`

- **Scrape interval**: 15 seconds
- **Evaluation interval**: 15 seconds
- **Retention**: 30 days
- **Target**: `app:8080/actuator/prometheus`

### Key Metrics Collected

- Application metrics (custom business metrics)
- JVM metrics (memory, GC, threads)
- HTTP metrics (request rate, latency)
- Database metrics (HikariCP connection pool)
- Cache metrics (hit/miss rates)

### Reload Configuration

After changing `prometheus.yml`:

```bash
# Reload Prometheus configuration
curl -X POST http://localhost:9090/-/reload
```

## 📈 Grafana Dashboards

### Main Dashboard: "LMIA Portal - Main Dashboard"

Includes panels for:
- Search request/error rates
- Search latency (p50, p95, p99)
- Database query performance
- Cache hit/miss rates
- File download metrics
- JVM memory usage
- Database connection pool
- Website URL lookup success rate
- HTTP request rates and response times

### Customizing Dashboards

1. Access Grafana: http://localhost:3000
2. Navigate to Dashboards
3. Edit the dashboard
4. Save changes

## 🚨 Alerting

### Alert Rules: `alerts.yml`

Configured alerts:

1. **HighSearchErrorRate** - Warning when error rate > 0.1 errors/sec
2. **SlowSearchQueries** - Warning when p95 latency > 2s
3. **SlowDatabaseQueries** - Warning when p95 query time > 1s
4. **HighCacheMissRate** - Info when miss rate > 50%
5. **ApplicationDown** - Critical when application is down
6. **HighMemoryUsage** - Warning when heap usage > 90%
7. **DatabaseConnectionPoolExhausted** - Warning when pool usage > 90%
8. **HighFileDownloadFailureRate** - Warning when error rate > 0.05 errors/sec
9. **HighWebsiteUrlLookupFailureRate** - Info when failure rate > 80%

### Viewing Alerts

1. Prometheus Alerts: http://localhost:9090/alerts
2. Grafana Alerting: http://localhost:3000/alerting

### Setting Up Alertmanager (Optional)

To receive alert notifications (email, Slack, etc.):

1. Uncomment Alertmanager service in `docker-compose.monitoring.yml`
2. Create `alertmanager.yml` configuration
3. Restart monitoring stack

Example `alertmanager.yml`:

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default-receiver'

receivers:
  - name: 'default-receiver'
    email_configs:
      - to: 'admin@example.com'
        from: 'alerts@example.com'
        smarthost: 'smtp.example.com:587'
        auth_username: 'alerts@example.com'
        auth_password: 'password'
```

## 📝 Logging Configuration

### Logback Configuration: `logback-spring.xml`

Features:
- **Console logging**: All logs to console
- **File logging**: Rotating log files (100MB, 30 days retention)
- **Error file**: Separate file for errors only (50MB, 90 days)
- **JSON logging**: Structured logs for log aggregation (optional)
- **Async logging**: Non-blocking file writes for better performance

### Log Levels

- **Application**: INFO (DEBUG in development)
- **Spring Framework**: WARN
- **Hibernate**: WARN (SQL logging in development only)

### Log File Locations

- Default: `/tmp/lmia-portal.log`
- Configurable via `LOG_FILE` environment variable

### Production Logging

For production, consider:
1. Using log aggregation (ELK, Loki, CloudWatch, etc.)
2. Enabling JSON logging (uncomment in `logback-spring.xml`)
3. Setting appropriate log levels via environment variables

Example environment variables:

```bash
LOG_FILE=/var/log/lmia-portal/lmia-portal.log
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_ORG_EXAMPLE=INFO
```

## 🔧 Configuration Options

### Prometheus Retention

Edit `docker-compose.monitoring.yml`:

```yaml
command:
  - '--storage.tsdb.retention.time=30d'  # Change retention period
```

### Grafana Admin Credentials

Edit `docker-compose.monitoring.yml`:

```yaml
environment:
  - GF_SECURITY_ADMIN_USER=admin
  - GF_SECURITY_ADMIN_PASSWORD=your-secure-password
```

### Alert Thresholds

Edit `alerts.yml` to adjust alert thresholds:

```yaml
- alert: SlowSearchQueries
  expr: histogram_quantile(0.95, rate(lmia_dataset_search_seconds_bucket[5m])) > 2
  # Change threshold from 2 to desired value
```

## 📊 Key Metrics to Monitor

### Application Health
- `up{job="lmia-portal"}` - Application availability
- `jvm_memory_used_bytes` - Memory usage
- `jvm_gc_pause_seconds` - GC performance

### Performance
- `lmia_dataset_search_seconds` - Search latency
- `lmia_database_query_seconds` - Database query time
- `http_server_requests_seconds` - HTTP response time

### Business Metrics
- `lmia_search_requests_total` - Search volume
- `lmia_search_errors_total` - Error rate
- `lmia_file_downloads_total` - File download activity
- `lmia_website_url_found_total` - Website URL discovery success

### Infrastructure
- `hikaricp_connections_active` - Database connection pool usage
- `lmia_cache_operations_total` - Cache performance

## 🛠️ Troubleshooting

### Prometheus Not Scraping

1. Check if application is running: `docker-compose ps`
2. Check Prometheus targets: http://localhost:9090/targets
3. Verify network connectivity: `docker network ls`
4. Check application logs: `docker-compose logs app`

### Grafana Not Showing Data

1. Verify Prometheus datasource: http://localhost:3000/datasources
2. Test query in Grafana: `up{job="lmia-portal"}`
3. Check Prometheus metrics: http://localhost:9090/graph?g0.expr=up

### Alerts Not Firing

1. Check alert rules: http://localhost:9090/alerts
2. Verify alert expressions in Prometheus: http://localhost:9090/graph
3. Check alert evaluation: http://localhost:9090/alerts

## 📚 Additional Resources

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Documentation](https://micrometer.io/docs)

## 🔐 Security Considerations

1. **Change default Grafana password** on first login
2. **Restrict Prometheus access** in production (use reverse proxy)
3. **Secure alert notifications** (use encrypted channels)
4. **Rotate log files** regularly to prevent disk space issues
5. **Monitor log file sizes** and set up alerts

## 🎯 Next Steps

1. ✅ Set up Prometheus and Grafana
2. ✅ Configure alerting rules
3. ✅ Import dashboards
4. ⏭️ Set up Alertmanager for notifications
5. ⏭️ Configure log aggregation (optional)
6. ⏭️ Set up automated backups for Grafana dashboards

