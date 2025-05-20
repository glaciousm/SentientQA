# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

### Build the Project
```bash
mvn clean install
```

### Run the Application
```bash
java -jar target/project-oracle-0.1.0-SNAPSHOT.jar
```

### Maven Profiles and Options
- Use `-DskipTests` to skip running tests during build
- Use `-Dmaven.test.skip=true` to skip compiling and running tests

### Running Tests
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=TestClassName

# Run a specific test method
mvn test -Dtest=TestClassName#testMethodName
```

## Project Architecture

Project Oracle is an AI-powered software testing platform implemented in Java that uses local open-source AI models to automatically generate, execute, and analyze tests for Java applications.

### Core Components

1. **Code Analysis** (`CodeAnalysisService`)
   - Parses Java AST via JavaParser into `MethodInfo` objects
   - Entry point for code analysis functionality

2. **AI Model Management** (`AIModelService`)
   - Manages loading, inference, and resource allocation for AI models
   - Uses Deep Java Library (DJL) + HuggingFace models
   - Models are auto-downloaded if not present

3. **Test Generation** (`TestGenerationService`)
   - Uses local GPT-2 models to generate JUnit 5 tests
   - Creates test cases from method signatures and code context

4. **Test Execution** (`EnhancedTestExecutionService`)
   - Compiles and runs generated tests with Java Compiler API & JUnit 5
   - Captures and analyzes test results

5. **Web Crawler** (`UICrawlerService`)
   - Discovers web application structure using Selenium WebDriver
   - Identifies pages, UI components, and interactions for test generation

6. **API Testing** (`APITestGenerationService`)
   - Discovers and tests REST API endpoints
   - Generates tests based on API behavior and responses

7. **Repositories**
   - `TestCaseRepository`: Stores test cases in memory and on disk (JSON)
   - `ElementRepository`: Manages UI element fingerprints for test healing

8. **Controllers**
   - REST controllers in the `com.projectoracle.rest` package
   - Endpoints follow `/api/v1/*` pattern

### Data Flow

1. Code is analyzed to extract method signatures and context
2. AI models generate JUnit 5 test code based on method info
3. Tests are compiled and executed by the execution engine
4. Results are collected and analyzed for reporting
5. Optional self-healing process attempts to fix failing tests

## Important Configuration Settings

### Application Properties

Key settings in `application.properties`:
- Server port: 8080
- Thread pool settings: core-size=2, max-size=4
- Output directories: models, cache, output

### AI Configuration

Key settings in `ai-config.properties`:
- Model directories and types: GPT-2 medium (default)
- Memory limits: 4GB default
- GPU usage: Disabled by default

### Crawler Configuration

Key settings in `CrawlerConfig.java`:
- Default max pages: 100
- Default max depth: 5
- Security exclusions: "logout", "sign-out", "delete", etc.

## API Endpoints

Common API endpoints:
- `/api/v1/health` - Health check
- `/api/v1/analyze/file` - Analyze Java source files
- `/api/v1/generate/test` - Generate tests for methods
- `/api/v1/enhanced-tests/{id}/execute` - Execute a specific test
- `/api/v1/reports/list` - List test reports

## Dependencies

- Spring Boot 3
- Deep Java Library (DJL) + HuggingFace models
- JavaParser for code analysis
- JUnit 5 for test execution
- Selenium WebDriver for UI crawling
- Jackson for JSON processing

## Important Development Notes

1. **Model Management**:
   - Models are downloaded automatically on first use
   - Large models require sufficient memory (min 4GB recommended)
   - `models/` directory must have write permissions

2. **Test Execution**:
   - Requires JDK (not just JRE) for dynamic compilation
   - Tests run in a sandboxed environment

3. **Crawler Configuration**:
   - Headless Chrome is used by default
   - WebDriver must be properly configured in the environment

4. **Current Limitations**:
   - Some features are in prototype stage (self-healing)
   - Performance can be slow on systems with limited resources

## Rules
1. **Never use placeholders or examples, always complete code**
2. **After each implementation compile the project and fix errors**
3. **Use Lombok as much as possible to reduce code size**
4. **Always keep in mind to write as compact code as possible in order not to have tokens maxing out**
5. **Less Patterns & Reg Ex, more AI usage**