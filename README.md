# Sentinel

## Overview

**Sentinel** is an AI-powered software testing platform implemented in Java. It uses local open-source AI models to automatically generate, execute, and analyze tests for Java applications. The system is designed as a Spring Boot application and follows the multi-phase architecture outlined in the Sentinel design specifications, which include components for code analysis, test generation, execution, reporting, and self-healing. The current implementation closely adheres to the specified architecture and flow.

## Installation and Setup

1. **Prerequisites**
    - Java 21 (JDK)
    - Maven 3.8+
    - 4 GB RAM minimum (8 GB recommended)
    - ~50 GB free disk space (for model files and data)

2. **Clone the Repository**
   ```bash
   git clone https://github.com/glaciousm/SentientQA.git
   cd SentientQA
   ```

3. **Build the Project**
   ```bash
   mvn clean install
   ```

4. **Run the Application**
   ```bash
   java -jar target/sentinel-0.1.0-SNAPSHOT.jar
   ```  
   The server starts on port 8080. On first run, it auto-downloads the default AI model to `output/models`.

## Usage

### Web Dashboard

Visit `http://localhost:8080` to access the built-in UI. You'll be prompted to log in with the default credentials (admin/admin):

- **Analyze Code**  
  Upload or point to Java source code; the system parses classes and methods.

- **Generate Tests**  
  Click to AI-generate JUnit 5 tests for analyzed methods.

- **Knowledge Integration**  
  Select external knowledge sources to enhance test generation with API docs, project documentation, historical test patterns, Jira issues, and Confluence pages.
  
- **Natural Language Queries**  
  Generate tests using plain English descriptions like "Test the login method with invalid credentials".

- **Execute Tests**  
  Run individual or all generated tests; view pass/fail and exception details.

- **Test Reports**  
  View/download HTML, JSON, CSV, or Excel reports with stats and coverage charts.

- **Self-Healing** *(Prototype)*  
  Attempt basic AI-driven fixes for failing tests.

- **H2 Console**
  Access the embedded database at `http://localhost:8080/h2-console` with credentials (sa/password).

### REST API

All features are available via `/api/v1/...` endpoints. Authentication is required for secured endpoints:

```bash
# First, get an authentication token (Basic Auth)
TOKEN=$(curl -s -u admin:admin http://localhost:8080/api/v1/auth/token)

# Health check (public endpoint)
curl http://localhost:8080/api/v1/health

# Analyze a file (secured)
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/analyze/file?filePath=src/Main.java"

# Generate tests for a method (secured)
curl -X POST http://localhost:8080/api/v1/generate/test \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{ "className":"com.example.Main", "methodName":"compute", ... }'

# Execute a test by ID (secured)
curl -X POST "http://localhost:8080/api/v1/enhanced-tests/123/execute?waitForResult=true" \
  -H "Authorization: Bearer $TOKEN"

# List reports (secured)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/reports/list

# Knowledge integration for test generation (secured)
curl -X POST http://localhost:8080/api/v1/knowledge/integrate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "methodSignature": "calculateTotal(int, double)",
    "knowledgeSources": [
      {"type": "api", "path": "docs/api-specs/orders.json", "format": "swagger", "enabled": true},
      {"type": "docs", "path": "docs/project/requirements.md", "format": "markdown", "enabled": true}
    ]
  }'

# Jira/Confluence integration for test generation (secured)
curl -X POST http://localhost:8080/api/v1/knowledge/integrate/jira \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "baseUrl": "https://your-company.atlassian.net",
    "username": "your-email@company.com",
    "apiToken": "your-api-token",
    "authType": "BASIC_AUTH"
  }' \
  --data-urlencode "methodSignature=calculateTotal(int, double)" \
  --data-urlencode "projectKey=PROJ"

# Generate tests using natural language (secured)
curl -X POST http://localhost:8080/api/v1/nlp/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "query": "Test the login method with invalid credentials",
    "targetClass": "UserAuthenticationService",
    "useAllAvailableKnowledge": true
  }'

# Get actuator health (admin only)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/management/health

# Cache Management
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/cache/stats  # Get cache statistics
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/cache/testCases  # Clear specific cache
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/cache  # Clear all caches
```

Refer to the source Javadoc or log output for full request/response models.

## Architecture Summary

- **Code Analysis** (`CodeAnalysisService`): Parses Java AST via JavaParser into `MethodInfo`.
- **Test Generation** (`TestGenerationService` + `AIModelService`): Uses a local GPT-2 model via DJL to generate JUnit 5 source code.
- **Knowledge Integration** (`KnowledgeIntegrationService`): Integrates external knowledge sources (API docs, project docs, code comments, test history) to improve test generation.
- **Atlassian Integration** (`JiraService` + `ConfluenceService`): Extracts test requirements, specs, and documentation from Jira/Confluence.
- **Natural Language Processing** (`NLPQueryService`): Allows test generation from natural language descriptions.
- **UI Crawling** (`UICrawlerService`): Headless Selenium crawler that discovers pages and UI components for end-to-end tests.
- **API Analysis** (`APITestGenerationService`): Discovers endpoints and generates API tests.
- **Test Repository** (`TestCaseRepository`): Persists test cases in memory and on disk (JSON).
- **Execution Engine** (`EnhancedTestExecutionService`): Compiles with Java Compiler API and runs JUnit 5, capturing results.
- **Reporting** (`TestReportGenerator`): Aggregates results and coverage, outputs HTML/JSON/CSV/Excel.
- **Self-Healing** (`TestHealingService`): AI fixes for failing tests.

## Dependencies

- Spring Boot 3.4.5
- Deep Java Library (DJL) 0.33.0 + HuggingFace GPT-2
- JavaParser 3.26.4
- JUnit Jupiter 5.12.2
- Selenium WebDriver 4.24.0
- WebDriverManager 5.7.0
- H2 Database (for repository persistence)
- Spring Security (for REST API protection)
- Spring Cache with Caffeine (for high-performance caching)
- Optional Redis support for distributed caching
- Jackson, Lombok, SLF4J/Logback
- Apache POI (Excel reports)
- JaCoCo (for code coverage analysis)

> **Note:** All unused dependencies have been removed, and the codebase has been optimized for performance and security.

## Conclusion

Sentinel delivers an end-to-end local QA automation workflow: from code analysis to AI-generated tests, execution, and reporting. See [ROADMAP.md](ROADMAP.md) for upcoming enhancements.

---

*License: MIT (see LICENSE file)*