# Canada LMIA Search Portal

Web application for searching historical and relevant information about companies that received or were denied Labour Market Impact Assessment (LMIA) in Canada.

## 🚀 Features

- **Company Search**: Find all LMIA decisions for a specific company
- **NOC Code Search**: Find all positions for a specific occupation (National Occupational Classification)
- **Province Filtering**: Search by Canadian provinces and territories
- **Status Filtering**: Search for approved (APPROVED) or denied (DENIED) applications
- **Date Filtering**: Search for decisions within a specific time range
- **Statistics**: Overall statistics for all records in the database
- **Data Export**: Export search results to CSV or Excel formats
- **API Documentation**: Interactive Swagger/OpenAPI documentation

## 🛠 Technologies

- **Backend**: Spring Boot 3.2.0, Spring Data JPA, Spring Security
- **Database**: PostgreSQL
- **Frontend**: HTML5, CSS3, JavaScript (Vanilla)
- **Data Processing**: Apache Commons CSV, Apache POI (for Excel files)
- **HTTP Client**: RestAssured
- **API Documentation**: SpringDoc OpenAPI (Swagger)
- **Caching**: Caffeine
- **Connection Pooling**: HikariCP
- **Monitoring**: Spring Boot Actuator

## 📋 Requirements

- Java 17+
- Maven 3.6+
- PostgreSQL 12+

**OR**

- Docker 20.10+
- Docker Compose 2.0+

## 🔧 Installation and Setup

### Option 1: Docker (Recommended)

The easiest way to run the application is using Docker Compose:

```bash
# Clone the repository
git clone https://github.com/VadimToptunov/CanadaLMIAByNOC.git
cd CanadaLMIAByNOC

# Build and start all services (PostgreSQL + Application)
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down
```

The application will be available at: `http://localhost:8080`

**Docker Compose includes:**
- PostgreSQL 15 database (automatically configured)
- Spring Boot application
- Automatic health checks
- Volume persistence for database data

**Environment variables** (can be overridden in `docker-compose.yml`):
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username (default: postgres)
- `SPRING_DATASOURCE_PASSWORD`: Database password (default: postgres)

### Option 2: Manual Setup

#### 1. Clone the repository

```bash
git clone https://github.com/VadimToptunov/CanadaLMIAByNOC.git
cd CanadaLMIAByNOC
```

#### 2. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE lmia_db;
```

#### 3. Configuration

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/lmia_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

#### 4. Build the project

```bash
mvn clean install
```

#### 5. Run the application

```bash
mvn spring-boot:run
```

The application will be available at: `http://localhost:8080`

### Building Docker Image

To build a standalone Docker image:

```bash
# Build the image
docker build -t canada-lmia-app:latest .

# Run the container (requires external PostgreSQL)
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/lmia_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  canada-lmia-app:latest
```

## 📊 Data Loading and Update Strategy

### Data Update Strategy

The application supports both **manual** and **automatic** data updates:

#### Manual Updates (Default)

By default, automatic updates are **disabled**. You can manually trigger data updates via:

**Via Web Interface:**
1. Open `http://localhost:8080`
2. Use API endpoints to load data:
   - `POST /api/admin/download` - download new datasets from open.canada.ca
   - `POST /api/admin/process` - process existing files from savedDatasets directory

**Via API:**
```bash
# Download and process data
curl -X POST -u admin:admin http://localhost:8080/api/admin/download

# Process existing files
curl -X POST -u admin:admin http://localhost:8080/api/admin/process
```

**Note**: Admin endpoints require authentication (username: `admin`, password: `admin`)

#### Automatic Scheduled Updates

The application includes a **scheduled task** for automatic data updates. This is useful because:
- LMIA data on open.canada.ca is typically updated **quarterly** (every 3 months)
- The scheduler can check for new data **weekly** to catch updates promptly
- Duplicate detection ensures no data is inserted twice

**To enable automatic updates:**

1. Edit `src/main/resources/application.properties`:
```properties
# Enable automatic data updates
app.data-update.enabled=true

# Schedule: First day of every month at 2:00 AM (default)
app.data-update.cron=0 0 2 1 * *

# Optional: Customize schedule
# Examples:
#   "0 0 2 1 * *" - First day of every month at 2:00 AM (recommended)
#   "0 0 2 * * SUN" - Every Sunday at 2:00 AM
#   "0 0 */6 * * *" - Every 6 hours
```

2. Restart the application

**Update Process:**
1. Downloads new datasets from open.canada.ca API
2. Processes CSV/Excel files
3. Saves to database with automatic duplicate detection
4. Logs results for monitoring

**Monitoring:**
- Check application logs for scheduled update results
- Use `/api/admin/stats` endpoint to verify record counts
- Failed updates are logged but don't stop the scheduler

**Recommendation:**
- For production: Enable monthly updates (default: 1st of month at 2 AM)
  - LMIA data is typically updated quarterly, so monthly checks are sufficient
- For development: Keep disabled and update manually as needed

## 🔍 API Endpoints

### Public Search Endpoints

- `GET /api/datasets/search` - Comprehensive search with filters
  - Parameters: `employer`, `nocCode`, `province`, `status`, `startDate`, `endDate`, `page`, `size`

- `GET /api/datasets/employer/{employerName}` - Search by company name

- `GET /api/datasets/noc/{nocCode}` - Search by NOC code

- `GET /api/datasets/statistics` - Get statistics

- `GET /api/datasets/export/csv` - Export to CSV
  - Parameters: `employer`, `nocCode`, `province`, `status`

- `GET /api/datasets/export/excel` - Export to Excel
  - Parameters: `employer`, `nocCode`, `province`, `status`

### Administrative Endpoints (Requires Authentication)

- `POST /api/admin/download` - Download new datasets
- `POST /api/admin/process` - Process files
- `GET /api/admin/stats` - System statistics

## 📚 API Documentation

Interactive API documentation is available through Swagger UI:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`

The documentation includes:
- Description of all endpoints
- Request and response parameters
- Usage examples
- Interactive API testing

## 🔐 Security

- **Authentication**: Basic authentication for admin endpoints
- **Authorization**: Role-based access control (ADMIN role)
- **Rate Limiting**: 100 requests per minute per IP address
- **Input Validation**: All API inputs are validated
- **Secure Error Handling**: No stack traces exposed to clients

## ⚡ Performance Features

- **Connection Pooling**: HikariCP with 20 max connections
- **Batch Operations**: Optimized batch inserts for better performance
- **Caching**: Caffeine cache for statistics (30 minutes TTL)
- **Database Indexes**: Optimized indexes for common queries
- **JPA Optimizations**: Batch inserts and updates enabled

## 🧪 Testing

The project includes comprehensive test coverage:

- **Unit Tests**: Data parsers, downloaders, export services
- **Integration Tests**: REST API endpoints, Spring context loading
- **Test Database**: H2 in-memory database for fast test execution

Run tests:
```bash
mvn test
```

## 📁 Project Structure

```
CanadaLMIAByNOC/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── org.example/
│   │   │   │   ├── AppMain.java          # Application entry point
│   │   │   │   └── AppBody.java          # Main service
│   │   │   ├── controller/               # REST controllers
│   │   │   ├── model/                     # JPA entities
│   │   │   ├── repository/                # Spring Data repositories
│   │   │   ├── service/                    # Business services
│   │   │   ├── dto/                        # Data transfer objects
│   │   │   ├── config/                     # Configuration classes
│   │   │   ├── exception/                 # Exception handlers
│   │   │   └── dataProcessors/             # Data parsers
│   │   └── resources/
│   │       ├── static/
│   │       │   └── index.html            # Web interface
│   │       └── application.properties    # Configuration
│   └── test/
│       └── java/                          # Test classes
└── pom.xml
```

## 🎯 Features Implemented

### Security
- ✅ Spring Security with role-based access control
- ✅ Rate limiting (100 requests/minute)
- ✅ Input validation
- ✅ Secure error handling

### Logging & Monitoring
- ✅ Request/Response logging middleware
- ✅ Error logging with context
- ✅ Performance monitoring (Actuator)
- ✅ Health checks

### Performance
- ✅ Connection pooling (HikariCP)
- ✅ Batch operations for database inserts
- ✅ Caching for statistics
- ✅ Database indexes optimization

### Testing
- ✅ Unit tests for core components
- ✅ Integration tests for REST API
- ✅ Test configuration with H2 database

### API Documentation
- ✅ Swagger/OpenAPI documentation
- ✅ Interactive API testing
- ✅ Comprehensive endpoint descriptions

## 🐛 Known Issues

- Date parsing from filenames may be inaccurate for some formats
- Some CSV files may have varying header structures
- Large Excel files may process slowly

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

The MIT License is one of the most permissive open-source licenses, allowing you to:
- ✅ Use commercially
- ✅ Modify
- ✅ Distribute
- ✅ Sublicense
- ✅ Private use

## 👤 Author

**Vadim Toptunov**

## 🙏 Acknowledgments

Data provided by [Open Canada](https://open.canada.ca/)

## 📞 Support

For issues, questions, or contributions, please open an issue in the repository.
