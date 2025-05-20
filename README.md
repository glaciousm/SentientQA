# Project Oracle

## Overview

**Project Oracle** is an AI-powered software testing platform implemented in Java. It uses local open-source AI models to automatically generate, execute, and analyze tests for Java applications. The system is designed as a Spring Boot application and follows the multi-phase architecture outlined in the Project Oracle design specifications, which include components for code analysis, test generation, execution, reporting, and self-healing. The current implementation closely adheres to the specified architecture and flow.

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
   java -jar target/project-oracle-0.1.0-SNAPSHOT.jar
   ```  
   The server starts on port 8080. On first run, it auto-downloads the default AI model to `output/models`.

## Usage

### Web Dashboard

Visit `http://localhost:8080` to access the built-in UI:

- **Analyze Code**  
  Upload or point to Java source code; the system parses classes and methods.

- **Generate Tests**  
  Click to AI-generate JUnit 5 tests for analyzed methods.

- **Execute Tests**  
  Run individual or all generated tests; view pass/fail and exception details.

- **Test Reports**  
  View/download HTML, JSON, CSV, or Excel reports with stats and coverage charts.

- **Self-Healing** *(Prototype)*  
  Attempt basic AI-driven fixes for failing tests.

### REST API

All features are available via `/api/v1/...` endpoints. Examples:

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Analyze a file
curl "http://localhost:8080/api/v1/analyze/file?filePath=src/Main.java"

# Generate tests for a method
curl -X POST http://localhost:8080/api/v1/generate/test      -H "Content-Type: application/json"      -d '{ "className":"com.example.Main", "methodName":"compute", ... }'

# Execute a test by ID
curl -X POST "http://localhost:8080/api/v1/enhanced-tests/123/execute?waitForResult=true"

# List reports
curl http://localhost:8080/api/v1/reports/list
```

Refer to the source Javadoc or log output for full request/response models.

## Architecture Summary

- **Code Analysis** (`CodeAnalysisService`): Parses Java AST via JavaParser into `MethodInfo`.
- **Test Generation** (`TestGenerationService` + `AIModelService`): Uses a local GPT-2 model via DJL to generate JUnit 5 source code.
- **UI Crawling** (`UICrawlerService`): Headless Selenium crawler that discovers pages and UI components for end-to-end tests.
- **API Analysis** (`APITestGenerationService`): Discovers endpoints and generates API tests.
- **Test Repository** (`TestCaseRepository`): Persists test cases in memory and on disk (JSON).
- **Execution Engine** (`EnhancedTestExecutionService`): Compiles with Java Compiler API and runs JUnit 5, capturing results.
- **Reporting** (`TestReportGenerator`): Aggregates results and coverage, outputs HTML/JSON/CSV/Excel.
- **Self-Healing** (`TestHealingService`): Prototype AI fixes for failing tests.

## Dependencies

- Spring Boot 3
- Deep Java Library (DJL) + HuggingFace GPT-2
- JavaParser
- JUnit 5
- Selenium WebDriver
- Jackson, Lombok, SLF4J/Logback
- Apache POI (Excel reports)

> **Note:** Unused dependencies in `pom.xml` (e.g., Tribuo, Lucene, RocksDB) can be removed to slim the build.

## Conclusion

Project Oracle delivers an end-to-end local QA automation workflow: from code analysis to AI-generated tests, execution, and reporting. See [ROADMAP.md](ROADMAP.md) for upcoming enhancements.

---

*License: MIT (see LICENSE file)*