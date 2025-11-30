# Monitoring Quick Reference

## 🚀 Start Monitoring

```bash
docker-compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d
```

## 🔗 Access

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## 📊 Key Metrics

- Search performance: `lmia_dataset_search_seconds`
- Error rate: `lmia_search_errors_total`
- Database: `lmia_database_query_seconds`
- Cache: `lmia_cache_operations_total`

## 🚨 Alerts

View alerts at: http://localhost:9090/alerts

## 📝 Logs

- Application logs: `/tmp/lmia-portal.log`
- Error logs: `/tmp/lmia-portal.log.error`

## 📖 Full Documentation

See [MONITORING_SETUP.md](MONITORING_SETUP.md) for detailed setup instructions.

