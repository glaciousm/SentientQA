# Project Oracle Development Roadmap

## Overview

This roadmap outlines the development goals for Project Oracle, divided into **Completed**, **Short-Term** (1–3 months), and **Long-Term** (3+ months) objectives. Each task includes its current status and priority level to help guide focus and resource allocation.

---

## Completed Tasks

These core functionalities have been fully implemented and validated against the design specifications:

| Task                                                                                              | Status     | Priority | Notes                                          |
|---------------------------------------------------------------------------------------------------|------------|----------|------------------------------------------------|
| **Code Analysis Service** (JavaParser integration)                                                | Completed  | -        | Parses Java AST into `MethodInfo` objects.     |
| **AI Test Generation** (TestGenerationService + AIModelService using local GPT-2 via DJL)         | Completed  | -        | Generates JUnit 5 test code from method input.|
| **Test Execution Engine** (EnhancedTestExecutionService with Java Compiler API & JUnit 5)         | Completed  | -        | Compiles and runs generated tests locally.     |
| **Test Reporting** (TestReportGenerator with HTML/JSON/CSV/Excel outputs)                         | Completed  | -        | Produces comprehensive test reports.           |
| **UI Crawler** (UICrawlerService using Selenium WebDriver)                                        | Completed  | -        | Discovers pages and UI components.             |
| **API Analysis & Test Generation** (APITestGenerationService & API endpoints)                     | Completed  | -        | Discovers endpoints and generates API tests.   |
| **Self-Healing Prototype** (TestHealingService basic AI-driven fixes)                              | Completed  | -        | Prototype for adjusting failing tests.         |

---

## Short-Term Goals (1–3 Months)

| Task                                                                                 | Status      | Priority | Notes                                          |
|--------------------------------------------------------------------------------------|-------------|----------|------------------------------------------------|
| 1. Complete placeholder features (proper crawl-stop, API discovery persistence)     | In Progress | High     | Implement stopping crawl & persist discovery.  |
| 2. Consolidate redundant services (remove simulated execution)                       | Planned     | Medium   | Merge ExecutionService into Enhanced version.  |
| 3. Remove unused dependencies (Tribuo, Lucene, RocksDB)                              | Planned     | Medium   | Slim down `pom.xml` until features are needed. |
| 4. Reorganize package structure (API vs. UI crawler separation)                      | Planned     | Low      | Clarify module boundaries.                     |
| 5. Improve error handling & thread safety in controllers/services                    | Planned     | High     | Validate inputs & handle AI model failures.    |
| 6. Enable model quantization (FP16/INT8)                                             | Planned     | High     | Reduce memory footprint for AI models.         |
| 7. Lazy-load AI models on first use                                                  | Planned     | Medium   | Speed up initial application startup.          |
| 8. Fine-tune thread pools for parallel tasks                                         | Planned     | Medium   | Optimize parallel test generation/execution.   |
| 9. Enhance UI responsiveness (lazy-load lists, loading indicators)                   | Planned     | Low      | Improve dashboard user experience.             |
| 10. Write unit tests for core services (CodeAnalysisService, TestGenerationService)  | Planned     | High     | Prevent regressions in critical modules.       |
| 11. Integrate static analysis tools (SpotBugs, Checkstyle)                           | Planned     | Low      | Enforce code quality and style.                |

---

## Long-Term Goals (3+ Months)

| Task                                                                                         | Status    | Priority | Notes                                              |
|----------------------------------------------------------------------------------------------|-----------|----------|----------------------------------------------------|
| 1. Knowledge Integration System (Jira/Confluence ingestion)                                  | Planned   | High     | Link tests to requirements for traceability.       |
| 2. NLP query interface for tests (e.g., “Generate tests for invalid login”)                  | Planned   | Medium   | Natural language to test generation.               |
| 3. Expand self-healing (AI-driven fixes & re-test)                                           | Planned   | High     | Automate test repair and validation.               |
| 4. Implement test prioritization (historical data & coverage heuristics)                     | Planned   | Medium   | Run high-risk tests first in CI pipelines.         |
| 5. Quality Intelligence Dashboard (failure trends, flakiness metrics)                        | Planned   | Medium   | Provide analytics and insights to users.           |
| 6. Support performance & security test generation                                            | Planned   | Low      | Generate JMH benchmarks and security tests.        |
| 7. CI/CD integration (Docker, Jenkins, GitHub Actions plugins)                               | Planned   | High     | Seamless enterprise pipeline compatibility.        |
| 8. Issue tracker automation (auto-create Jira tickets for failures)                          | Planned   | Medium   | Close the feedback loop with development workflow. |
| 9. Plugin architecture for custom generators & models                                        | Planned   | Low      | Enable extensibility for domain-specific needs.    |
| 10. Cross-language support (e.g., Python)                                                    | Planned   | Low      | Broaden platform beyond Java.                      |
| 11. Scalability improvements (distributed storage & execution)                               | Planned   | Medium   | Handle enterprise-scale codebases & test suites.   |
| 12. Model upgrades & fine-tuning on user code                                                | Planned   | Medium   | Improve test quality with advanced AI models.      |

---

*Legend:*
- **Status:**
  - `Completed` = Task fully implemented
  - `In Progress` = Actively being worked on
  - `Planned` = Scheduled for future sprints

- **Priority:**
  - `High` = Critical for next releases
  - `Medium` = Important, but not blocking
  - `Low` = Nice-to-have or exploratory  
