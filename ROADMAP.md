# Project Oracle Development Roadmap

## Overview

This roadmap outlines the development goals for Project Oracle, divided into **Completed**, **Implementation Stages**, **Short-Term** (1–3 months), and **Long-Term** (3+ months) objectives. Each task includes its current status and priority level to help guide focus and resource allocation.

---

## Completed Tasks

These core functionalities have been fully implemented and validated:

| Task                                                                                              | Status     | Priority | Notes                                          |
|---------------------------------------------------------------------------------------------------|------------|----------|------------------------------------------------|
| **Code Analysis Service** (JavaParser integration)                                                | Completed  | -        | Parses Java AST into `MethodInfo` objects.     |
| **AI Test Generation** (TestGenerationService + AIModelService using local GPT-2 via DJL)         | Completed  | -        | Generates JUnit 5 test code from method input. |
| **Test Execution Engine** (EnhancedTestExecutionService with Java Compiler API & JUnit 5)         | Completed  | -        | Compiles and runs generated tests locally.     |
| **Test Reporting** (TestReportGenerator with HTML/JSON/CSV/Excel outputs)                         | Completed  | -        | Produces comprehensive test reports.           |
| **UI Crawler** (UICrawlerService using Selenium WebDriver)                                        | Completed  | -        | Discovers pages and UI components.             |
| **API Analysis & Test Generation** (APITestGenerationService & API endpoints)                     | Completed  | -        | Discovers endpoints and generates API tests.   |
| **Self-Healing Prototype** (TestHealingService basic AI-driven fixes)                              | Completed  | -        | Prototype for adjusting failing tests.         |
| **Knowledge Integration System** (KnowledgeIntegrationService for API docs, code comments, etc.)   | Completed  | -        | Integrates external knowledge with test gen.   |

---

## Implementation Stages

To manage implementation efficiently (given token constraints), tasks are grouped into concise stages:

- **Stage 1 (Immediate Short-Term):** ✅ COMPLETED
  1. Complete placeholder features (Completed, High)
  2. Consolidate redundant services (Completed, Medium)
  3. Remove unused dependencies (Completed, Medium)

- **Stage 2 (Short-Term):** ✅ COMPLETED
  1. Reorganize package structure (Completed, Low)
  2. Improve error handling & thread safety (Completed, High)
  3. Enable model quantization (Completed, High)

- **Stage 3 (Short-Term):** ✅ COMPLETED
  1. Lazy-load AI models on first use (Completed, Medium)
  2. Fine-tune thread pools for parallel tasks (Completed, Medium)
  3. Enhance UI responsiveness (Completed, Low)

- **Stage 4 (Short-Term):** ✅ COMPLETED
  1. Write unit tests for core services (Completed, High)
  2. Integrate static analysis tools (SpotBugs, Checkstyle) (Completed, Low)

- **Stage 5 (Mid-Term):** ✅ COMPLETED
  1. Knowledge Integration System (API docs, project docs, code comments, test history) (Completed, High)
  2. Jira/Confluence ingestion for knowledge integration (Completed, Medium)
  3. NLP query interface for tests (Completed, Medium)

- **Stage 6 (Mid-Term):** ✅ COMPLETED
  1. Expand self-healing features (Completed, High)
  2. Implement test prioritization (Completed, Medium)

- **Stage 7 (Mid-Term):** ✅ COMPLETED
  1. Quality Intelligence Dashboard (Completed, Medium)
  2. Support performance & security test generation (Completed, Low)

- **Stage 8 (Current Focus): UI Integration**
  1. Comprehensive dashboard UI for quality metrics (In Progress, High)
  2. Test healing and prioritization UI (In Progress, High)
  3. Security and performance test UI (In Progress, Medium)
  4. Responsive layout and unified experience (In Progress, Medium)
  5. Real-time updates and notifications (In Progress, Low)

- **Stage 9 (Long-Term):**
  1. CI/CD integration (Planned, High)
  2. Issue tracker automation (Planned, Medium)

- **Stage 10 (Long-Term):**
  1. Plugin architecture for custom generators & models (Planned, Low)
  2. Cross-language support (Planned, Low)

- **Stage 11 (Long-Term):**
  1. Scalability improvements (Planned, Medium)
  2. Model upgrades & fine-tuning on user code (Planned, Medium)

---

## Short-Term Goals (1–3 Months)

| Task                                                                                 | Status     | Priority | Notes                                          |
|--------------------------------------------------------------------------------------|------------|----------|------------------------------------------------|
| 1. ✅ Complete placeholder features (proper crawl-stop, API discovery persistence)    | Completed  | High     | Implement stopping crawl & persist discovery.  |
| 2. ✅ Consolidate redundant services (remove simulated execution)                     | Completed  | Medium   | Merge ExecutionService into Enhanced version.  |
| 3. ✅ Remove unused dependencies (Tribuo, Lucene, RocksDB)                            | Completed  | Medium   | Slim down `pom.xml` until needed.              |
| 4. ✅ Reorganize package structure (API vs. UI crawler separation)                    | Completed  | Low      | Clarify module boundaries.                     |
| 5. ✅ Improve error handling & thread safety in controllers/services                  | Completed  | High     | Validate inputs & handle AI model failures.    |
| 6. ✅ Enable model quantization (FP16/INT8)                                           | Completed  | High     | Reduce memory footprint for AI models.         |
| 7. ✅ Add database, security, and cache configuration                                 | Completed  | High     | Support repository persistence and security.   |
| 8. ✅ Add WebDriverManager for Selenium browser management                            | Completed  | Medium   | Automatic browser driver management.           |
| 9. ✅ Improve thread pools for parallel tasks                                         | Completed  | Medium   | Optimize parallel test generation/execution.   |
| 10. ✅ Lazy-load AI models on first use                                               | Completed  | Medium   | Speed up initial startup.                      |
| 11. ✅ Enhance UI responsiveness (lazy-load lists, loading indicators)                | Completed  | Low      | Improve dashboard UX.                          |
| 12. ✅ Write unit tests for core services                                             | Completed  | High     | Prevent regressions in critical modules.       |
| 13. ✅ Integrate static analysis tools (SpotBugs, Checkstyle)                         | Completed  | Low      | Enforce code quality and style.                |
| 14. ✅ Implement Knowledge Integration System for test enhancement                    | Completed  | High     | Integrate external knowledge with test gen.    |
| 15. ✅ Add Jira/Confluence integration for test enhancement                               | Completed  | Medium   | Extract test requirements from Atlassian.      |
| 16. ✅ Implement NLP interface for test generation                                        | Completed  | Medium   | Generate tests from natural language queries.  |

---

## Current and Long-Term Goals

| Task                                                                                         | Status      | Priority | Notes                                              |
|----------------------------------------------------------------------------------------------|-------------|----------|----------------------------------------------------|
| 1. Knowledge Integration System (API docs, project docs, code comments, test history)        | Completed   | High     | External knowledge enrichment for test generation. |
| 2. Jira/Confluence ingestion for knowledge integration                                       | Completed   | Medium   | Link tests to requirements for traceability.       |
| 3. NLP query interface for tests (e.g., "Generate tests for invalid login")                  | Completed   | Medium   | Natural language to test generation.               |
| 4. Expand self-healing (AI-driven fixes & re-test)                                           | Completed   | High     | Automate test repair and validation.               |
| 5. Implement test prioritization (historical data & coverage heuristics)                     | Completed   | Medium   | Run high-risk tests first in CI pipelines.         |
| 6. Quality Intelligence Dashboard (failure trends, flakiness metrics)                        | Completed   | Medium   | Provide analytics and insights.                    |
| 7. Support performance & security test generation                                            | Completed   | Low      | Generate JMH benchmarks and security tests.        |
| 8. Comprehensive UI for quality metrics visualization                                        | In Progress | High     | Interactive dashboards with visualizations.        |
| 9. Test healing and prioritization UI                                                        | In Progress | High     | User-friendly interface for test management.       |
| 10. Security and performance testing UI                                                      | In Progress | Medium   | Integrated GUI for specialized testing.            |
| 11. Responsive unified user experience                                                       | In Progress | Medium   | Consistent experience across devices.              |
| 12. Real-time test execution updates                                                         | In Progress | Low      | WebSocket updates for live test results.           |
| 13. CI/CD integration (Docker, Jenkins, GitHub Actions plugins)                              | Planned     | High     | Seamless enterprise pipeline compatibility.        |
| 14. Issue tracker automation (auto-create Jira tickets for failures)                         | Planned     | Medium   | Close the feedback loop with development workflow. |
| 15. Plugin architecture for custom generators & models                                       | Planned     | Low      | Enable extensibility for domain-specific needs.    |
| 16. Cross-language support (e.g., Python)                                                    | Planned     | Low      | Broaden platform beyond Java.                      |
| 17. Scalability improvements (distributed storage & execution)                               | Planned     | Medium   | Handle enterprise-scale projects.                  |
| 18. Model upgrades & fine-tuning on user code                                                | Planned     | Medium   | Improve test quality with advanced AI models.      |

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