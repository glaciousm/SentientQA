package com.projectoracle.service.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.reporting.TestResultAnalyzer.TestResultReport;
import com.projectoracle.service.reporting.TestCoverageCalculator.CoverageReport;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.FileOutputStream;

/**
 * Service for generating comprehensive test reports.
 * Creates HTML, JSON, and other report formats based on test results and coverage data.
 */
@Service
public class TestReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TestReportGenerator.class);

    @Autowired
    private TestResultAnalyzer resultAnalyzer;

    @Autowired
    private TestCoverageCalculator coverageCalculator;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.directories.output:output}")
    private String outputDir;

    /**
     * Generate a comprehensive test report
     */
    public String generateReport() {
        logger.info("Generating comprehensive test report");

        try {
            // Create report directory
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String reportDirName = "test-report-" + timestamp;
            Path reportDir = Paths.get(outputDir, "reports", reportDirName);
            Files.createDirectories(reportDir);

            // Get analysis data
            TestResultReport resultReport = resultAnalyzer.analyzeResults();
            CoverageReport coverageReport = coverageCalculator.calculateCoverage();

            // Generate JSON reports
            writeJsonReport(reportDir.resolve("test-results.json"), resultReport);
            writeJsonReport(reportDir.resolve("test-coverage.json"), coverageReport);

            // Generate HTML report
            String htmlReport = generateHtmlReport(resultReport, coverageReport);
            Files.writeString(reportDir.resolve("index.html"), htmlReport, StandardOpenOption.CREATE);

            // Generate CSV detail reports
            generateDetailReports(reportDir, resultReport, coverageReport);

            // Generate Excel report
            generateExcelReport(reportDir, resultReport, coverageReport);

            logger.info("Test report generated at {}", reportDir);
            return reportDir.toString();

        } catch (Exception e) {
            logger.error("Error generating test report", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Write a JSON report
     */
    private void writeJsonReport(Path file, Object data) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        Files.writeString(file, json, StandardOpenOption.CREATE);
    }

    /**
     * Generate detail reports in CSV format
     */
    private void generateDetailReports(Path reportDir, TestResultReport resultReport,
            CoverageReport coverageReport) throws IOException {
        // Generate test details CSV
        List<TestCase> allTests = testCaseRepository.findAll();
        StringBuilder testCsv = new StringBuilder();

        // CSV header
        testCsv.append("ID,Name,Class,Method,Type,Status,Last Executed,Execution Time (ms),Error Message\n");

        // Add test data
        for (TestCase test : allTests) {
            testCsv.append(escapeCsv(test.getId().toString())).append(",")
                   .append(escapeCsv(test.getName())).append(",")
                   .append(escapeCsv(test.getClassName())).append(",")
                   .append(escapeCsv(test.getMethodName())).append(",")
                   .append(escapeCsv(test.getType().toString())).append(",")
                   .append(escapeCsv(test.getStatus().toString())).append(",")
                   .append(escapeCsv(test.getLastExecutedAt() != null ?
                           test.getLastExecutedAt().toString() : "")).append(",")
                   .append(test.getLastExecutionResult() != null ?
                           test.getLastExecutionResult().getExecutionTimeMs() : "").append(",")
                   .append(escapeCsv(test.getLastExecutionResult() != null ?
                           test.getLastExecutionResult().getErrorMessage() : ""))
                   .append("\n");
        }

        Files.writeString(reportDir.resolve("test-details.csv"), testCsv.toString(), StandardOpenOption.CREATE);

        // Generate coverage details CSV
        StringBuilder coverageCsv = new StringBuilder();

        // CSV header
        coverageCsv.append("Package,Class,Method,Covered\n");

        // Add coverage data
        for (TestCoverageCalculator.PackageCoverage pkg : coverageReport.getPackageCoverage()) {
            for (Map.Entry<String, TestCoverageCalculator.ClassCoverage> classEntry :
                    pkg.getClassCoverage().entrySet()) {

                TestCoverageCalculator.ClassCoverage classCov = classEntry.getValue();

                for (TestCoverageCalculator.MethodCoverage method : classCov.getMethods()) {
                    coverageCsv.append(escapeCsv(pkg.getPackageName())).append(",")
                               .append(escapeCsv(classCov.getClassName())).append(",")
                               .append(escapeCsv(method.getMethodName())).append(",")
                               .append(method.isCovered() ? "Yes" : "No")
                               .append("\n");
                }
            }
        }

        Files.writeString(reportDir.resolve("coverage-details.csv"), coverageCsv.toString(), StandardOpenOption.CREATE);

        // Generate failure patterns CSV
        if (resultReport.getTopFailurePatterns() != null && !resultReport.getTopFailurePatterns().isEmpty()) {
            StringBuilder patternsCsv = new StringBuilder();

            // CSV header
            patternsCsv.append("Pattern,Occurrences,Affected Tests\n");

            // Add patterns data
            for (TestResultAnalyzer.FailurePattern pattern : resultReport.getTopFailurePatterns()) {
                patternsCsv.append(escapeCsv(pattern.getPattern())).append(",")
                           .append(pattern.getOccurrences()).append(",")
                           .append(escapeCsv(pattern.getAffectedTests().stream()
                                                    .map(TestCase::getName)
                                                    .collect(Collectors.joining("; "))))
                           .append("\n");
            }

            Files.writeString(reportDir.resolve("failure-patterns.csv"), patternsCsv.toString(), StandardOpenOption.CREATE);
        }
    }

    /**
     * Escape a string for CSV format
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, newline, or double quote, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    /**
     * Generate HTML report
     */
    private String generateHtmlReport(TestResultReport resultReport, CoverageReport coverageReport) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"en\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <title>Sentinel Test Report</title>\n")
            .append("  <link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n")
            .append("  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.0/font/bootstrap-icons.css\">\n")
            .append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n")
            .append("  <style>\n")
            .append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif; }\n")
            .append("    .card { margin-bottom: 20px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); }\n")
            .append("    .stat-card { text-align: center; padding: 20px; }\n")
            .append("    .stat-card .value { font-size: 2rem; font-weight: bold; }\n")
            .append("    .stat-card .label { font-size: 1rem; color: #6c757d; }\n")
            .append("    .trend-up { color: #28a745; }\n")
            .append("    .trend-down { color: #dc3545; }\n")
            .append("    .chart-container { height: 300px; }\n")
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("<div class=\"container-fluid p-4\">\n")
            .append("  <h1 class=\"mb-4\">Sentinel Test Report</h1>\n")
            .append("  <p class=\"text-muted\">Generated on: ").append(resultReport.getGeneratedAt()).append("</p>\n");

        // Summary section
        html.append("  <div class=\"row mb-4\">\n")
            .append("    <div class=\"col-md-3\">\n")
            .append("      <div class=\"card stat-card\">\n")
            .append("        <div class=\"value\">").append(resultReport.getTotalTests()).append("</div>\n")
            .append("        <div class=\"label\">Total Tests</div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"col-md-3\">\n")
            .append("      <div class=\"card stat-card\">\n")
            .append("        <div class=\"value\" style=\"color: #28a745;\">").append(resultReport.getPassedTests()).append("</div>\n")
            .append("        <div class=\"label\">Passed Tests</div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"col-md-3\">\n")
            .append("      <div class=\"card stat-card\">\n")
            .append("        <div class=\"value\" style=\"color: #dc3545;\">").append(resultReport.getFailedTests() + resultReport.getBrokenTests()).append("</div>\n")
            .append("        <div class=\"label\">Failed/Broken Tests</div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"col-md-3\">\n")
            .append("      <div class=\"card stat-card\">\n")
            .append("        <div class=\"value\">").append(String.format("%.1f%%", resultReport.getSuccessRate())).append("</div>\n")
            .append("        <div class=\"label\">Success Rate ");

        // Add trend indicator
        if (resultReport.getSuccessRateTrend() > 0) {
            html.append("<span class=\"trend-up\"><i class=\"bi bi-arrow-up\"></i> ")
                .append(String.format("%.1f%%", resultReport.getSuccessRateTrend()))
                .append("</span>");
        } else if (resultReport.getSuccessRateTrend() < 0) {
            html.append("<span class=\"trend-down\"><i class=\"bi bi-arrow-down\"></i> ")
                .append(String.format("%.1f%%", Math.abs(resultReport.getSuccessRateTrend())))
                .append("</span>");
        }

        html.append("</div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("  </div>\n");

        // Charts section
        html.append("  <div class=\"row mb-4\">\n")
            .append("    <div class=\"col-md-6\">\n")
            .append("      <div class=\"card\">\n")
            .append("        <div class=\"card-header\">Test Status Distribution</div>\n")
            .append("        <div class=\"card-body chart-container\">\n")
            .append("          <canvas id=\"statusChart\"></canvas>\n")
            .append("        </div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"col-md-6\">\n")
            .append("      <div class=\"card\">\n")
            .append("        <div class=\"card-header\">Test Type Distribution</div>\n")
            .append("        <div class=\"card-body chart-container\">\n")
            .append("          <canvas id=\"typeChart\"></canvas>\n")
            .append("        </div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("  </div>\n");

        // Coverage section
        html.append("  <div class=\"row mb-4\">\n")
            .append("    <div class=\"col-md-6\">\n")
            .append("      <div class=\"card\">\n")
            .append("        <div class=\"card-header\">Test Coverage</div>\n")
            .append("        <div class=\"card-body\">\n")
            .append("          <div class=\"row align-items-center\">\n")
            .append("            <div class=\"col-md-4 text-center\">\n")
            .append("              <div class=\"value\" style=\"font-size: 2rem; font-weight: bold;\">")
            .append(String.format("%.1f%%", coverageReport.getCoveragePercentage()))
            .append("</div>\n")
            .append("              <div class=\"label\">Code Coverage</div>\n")
            .append("            </div>\n")
            .append("            <div class=\"col-md-8\">\n")
            .append("              <div class=\"progress\" style=\"height: 30px;\">\n")
            .append("                <div class=\"progress-bar bg-success\" role=\"progressbar\" style=\"width: ")
            .append(coverageReport.getCoveragePercentage()).append("%;\" aria-valuenow=\"")
            .append(coverageReport.getCoveragePercentage()).append("\" aria-valuemin=\"0\" aria-valuemax=\"100\">")
            .append(String.format("%.1f%%", coverageReport.getCoveragePercentage())).append("</div>\n")
            .append("              </div>\n")
            .append("              <div class=\"mt-2 text-muted\">")
            .append(coverageReport.getCoveredMethods()).append(" of ").append(coverageReport.getTotalMethods())
            .append(" methods covered</div>\n")
            .append("            </div>\n")
            .append("          </div>\n")
            .append("        </div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("    <div class=\"col-md-6\">\n")
            .append("      <div class=\"card\">\n")
            .append("        <div class=\"card-header\">Daily Test Results</div>\n")
            .append("        <div class=\"card-body chart-container\">\n")
            .append("          <canvas id=\"trendChart\"></canvas>\n")
            .append("        </div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("  </div>\n");

        // Top failure patterns
        if (resultReport.getTopFailurePatterns() != null && !resultReport.getTopFailurePatterns().isEmpty()) {
            html.append("  <div class=\"row mb-4\">\n")
                .append("    <div class=\"col-12\">\n")
                .append("      <div class=\"card\">\n")
                .append("        <div class=\"card-header\">Top Failure Patterns</div>\n")
                .append("        <div class=\"card-body\">\n")
                .append("          <div class=\"table-responsive\">\n")
                .append("            <table class=\"table table-striped\">\n")
                .append("              <thead>\n")
                .append("                <tr>\n")
                .append("                  <th>Error Pattern</th>\n")
                .append("                  <th>Occurrences</th>\n")
                .append("                  <th>Affected Tests</th>\n")
                .append("                </tr>\n")
                .append("              </thead>\n")
                .append("              <tbody>\n");

            for (TestResultAnalyzer.FailurePattern pattern : resultReport.getTopFailurePatterns()) {
                html.append("                <tr>\n")
                    .append("                  <td>").append(escapeHtml(pattern.getPattern())).append("</td>\n")
                    .append("                  <td>").append(pattern.getOccurrences()).append("</td>\n")
                    .append("                  <td>");

                List<String> testNames = pattern.getAffectedTests().stream()
                                                .map(TestCase::getName)
                                                .limit(3)
                                                .collect(Collectors.toList());

                html.append(String.join(", ", testNames));

                if (pattern.getAffectedTests().size() > 3) {
                    html.append(" and ").append(pattern.getAffectedTests().size() - 3).append(" more");
                }

                html.append("</td>\n")
                    .append("                </tr>\n");
            }

            html.append("              </tbody>\n")
                .append("            </table>\n")
                .append("          </div>\n")
                .append("        </div>\n")
                .append("      </div>\n")
                .append("    </div>\n")
                .append("  </div>\n");
        }

        // Slowest tests
        if (resultReport.getSlowestTests() != null && !resultReport.getSlowestTests().isEmpty()) {
            html.append("  <div class=\"row mb-4\">\n")
                .append("    <div class=\"col-md-6\">\n")
                .append("      <div class=\"card\">\n")
                .append("        <div class=\"card-header\">Slowest Tests</div>\n")
                .append("        <div class=\"card-body\">\n")
                .append("          <div class=\"table-responsive\">\n")
                .append("            <table class=\"table table-sm\">\n")
                .append("              <thead>\n")
                .append("                <tr>\n")
                .append("                  <th>Test Name</th>\n")
                .append("                  <th>Execution Time (ms)</th>\n")
                .append("                </tr>\n")
                .append("              </thead>\n")
                .append("              <tbody>\n");

            for (TestCase test : resultReport.getSlowestTests()) {
                if (test.getLastExecutionResult() != null) {
                    html.append("                <tr>\n")
                        .append("                  <td>").append(escapeHtml(test.getName())).append("</td>\n")
                        .append("                  <td>").append(test.getLastExecutionResult().getExecutionTimeMs()).append("</td>\n")
                        .append("                </tr>\n");
                }
            }

            html.append("              </tbody>\n")
                .append("            </table>\n")
                .append("          </div>\n")
                .append("        </div>\n")
                .append("      </div>\n")
                .append("    </div>\n");

            // Flaky tests
            if (resultReport.getPotentiallyFlakyTests() != null && !resultReport.getPotentiallyFlakyTests().isEmpty()) {
                html.append("    <div class=\"col-md-6\">\n")
                    .append("      <div class=\"card\">\n")
                    .append("        <div class=\"card-header\">Potentially Flaky Tests</div>\n")
                    .append("        <div class=\"card-body\">\n")
                    .append("          <div class=\"table-responsive\">\n")
                    .append("            <table class=\"table table-sm\">\n")
                    .append("              <thead>\n")
                    .append("                <tr>\n")
                    .append("                  <th>Test Name</th>\n")
                    .append("                  <th>Status</th>\n")
                    .append("                </tr>\n")
                    .append("              </thead>\n")
                    .append("              <tbody>\n");

                for (TestCase test : resultReport.getPotentiallyFlakyTests()) {
                    html.append("                <tr>\n")
                        .append("                  <td>").append(escapeHtml(test.getName())).append("</td>\n")
                        .append("                  <td>").append(test.getStatus()).append("</td>\n")
                        .append("                </tr>\n");
                }

                html.append("              </tbody>\n")
                    .append("            </table>\n")
                    .append("          </div>\n")
                    .append("        </div>\n")
                    .append("      </div>\n")
                    .append("    </div>\n");
            }

            html.append("  </div>\n");
        }

        // Package coverage breakdown
        html.append("  <div class=\"row mb-4\">\n")
            .append("    <div class=\"col-12\">\n")
            .append("      <div class=\"card\">\n")
            .append("        <div class=\"card-header\">Package Coverage</div>\n")
            .append("        <div class=\"card-body\">\n")
            .append("          <div class=\"table-responsive\">\n")
            .append("            <table class=\"table table-striped\">\n")
            .append("              <thead>\n")
            .append("                <tr>\n")
            .append("                  <th>Package</th>\n")
            .append("                  <th>Methods</th>\n")
            .append("                  <th>Coverage</th>\n")
            .append("                </tr>\n")
            .append("              </thead>\n")
            .append("              <tbody>\n");

        if (coverageReport.getPackageCoverage() != null) {
            for (TestCoverageCalculator.PackageCoverage pkg : coverageReport.getPackageCoverage()) {
                html.append("                <tr>\n")
                    .append("                  <td>").append(escapeHtml(pkg.getPackageName())).append("</td>\n")
                    .append("                  <td>").append(pkg.getCoveredMethods()).append("/").append(pkg.getTotalMethods()).append("</td>\n")
                    .append("                  <td>\n")
                    .append("                    <div class=\"progress\" style=\"height: 20px;\">\n")
                    .append("                      <div class=\"progress-bar bg-success\" role=\"progressbar\" style=\"width: ")
                    .append(pkg.getCoveragePercentage()).append("%;\" aria-valuenow=\"")
                    .append(pkg.getCoveragePercentage()).append("\" aria-valuemin=\"0\" aria-valuemax=\"100\">")
                    .append(String.format("%.1f%%", pkg.getCoveragePercentage())).append("</div>\n")
                    .append("                    </div>\n")
                    .append("                  </td>\n")
                    .append("                </tr>\n");
            }
        }

        html.append("              </tbody>\n")
            .append("            </table>\n")
            .append("          </div>\n")
            .append("        </div>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("  </div>\n");

        // Footer
        html.append("  <div class=\"text-center text-muted mt-4\">\n")
            .append("    <p>Generated by Sentinel - AI-Powered QA Platform</p>\n")
            .append("  </div>\n")
            .append("</div>\n");

        // JavaScript for charts
        html.append("<script>\n")
            // Status chart
            .append("document.addEventListener('DOMContentLoaded', function() {\n")
            .append("  // Status chart\n")
            .append("  const statusCtx = document.getElementById('statusChart').getContext('2d');\n")
            .append("  new Chart(statusCtx, {\n")
            .append("    type: 'doughnut',\n")
            .append("    data: {\n")
            .append("      labels: ['Passed', 'Failed', 'Broken', 'Healed'],\n")
            .append("      datasets: [{\n")
            .append("        data: [")
            .append(resultReport.getPassedTests()).append(", ")
            .append(resultReport.getFailedTests()).append(", ")
            .append(resultReport.getBrokenTests()).append(", ")
            .append(resultReport.getHealedTests()).append("],\n")
            .append("        backgroundColor: ['#28a745', '#dc3545', '#fd7e14', '#17a2b8']\n")
            .append("      }]\n")
            .append("    },\n")
            .append("    options: {\n")
            .append("      responsive: true,\n")
            .append("      maintainAspectRatio: false,\n")
            .append("      plugins: {\n")
            .append("        legend: {\n")
            .append("          position: 'right'\n")
            .append("        }\n")
            .append("      }\n")
            .append("    }\n")
            .append("  });\n");

        // Type chart
        html.append("  // Type chart\n")
            .append("  const typeCtx = document.getElementById('typeChart').getContext('2d');\n")
            .append("  new Chart(typeCtx, {\n")
            .append("    type: 'bar',\n")
            .append("    data: {\n")
            .append("      labels: [");

        // Add test type labels
        if (resultReport.getTestTypeDistribution() != null) {
            html.append(resultReport.getTestTypeDistribution().keySet().stream()
                                    .map(type -> "'" + type + "'")
                                    .collect(Collectors.joining(", ")));
        }

        html.append("],\n")
            .append("      datasets: [{\n")
            .append("        label: 'Tests by Type',\n")
            .append("        data: [");

        // Add test type counts
        if (resultReport.getTestTypeDistribution() != null) {
            html.append(resultReport.getTestTypeDistribution().values().stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", ")));
        }

        html.append("],\n")
            .append("        backgroundColor: '#4361ee'\n")
            .append("      }]\n")
            .append("    },\n")
            .append("    options: {\n")
            .append("      responsive: true,\n")
            .append("      maintainAspectRatio: false,\n")
            .append("      scales: {\n")
            .append("        y: {\n")
            .append("          beginAtZero: true,\n")
            .append("          ticks: {\n")
            .append("            precision: 0\n")
            .append("          }\n")
            .append("        }\n")
            .append("      }\n")
            .append("    }\n")
            .append("  });\n");

        // Trend chart
        html.append("  // Trend chart\n")
            .append("  const trendCtx = document.getElementById('trendChart').getContext('2d');\n")
            .append("  new Chart(trendCtx, {\n")
            .append("    type: 'line',\n")
            .append("    data: {\n")
            .append("      labels: [");

        // Add trend dates
        if (resultReport.getDailyTrends() != null) {
            html.append(resultReport.getDailyTrends().stream()
                                    .map(point -> "'" + point.getDate() + "'")
                                    .collect(Collectors.joining(", ")));
        }

        html.append("],\n")
            .append("      datasets: [{\n")
            .append("        label: 'Passed',\n")
            .append("        data: [");

        // Add success trend data
        if (resultReport.getDailyTrends() != null) {
            html.append(resultReport.getDailyTrends().stream()
                                    .map(point -> String.valueOf(point.getSuccess()))
                                    .collect(Collectors.joining(", ")));
        }

        html.append("],\n")
            .append("        borderColor: '#28a745',\n")
            .append("        backgroundColor: 'rgba(40, 167, 69, 0.1)',\n")
            .append("        fill: true\n")
            .append("      }, {\n")
            .append("        label: 'Failed',\n")
            .append("        data: [");

        // Add failure trend data
        if (resultReport.getDailyTrends() != null) {
            html.append(resultReport.getDailyTrends().stream()
                                    .map(point -> String.valueOf(point.getFailure()))
                                    .collect(Collectors.joining(", ")));
        }

        html.append("],\n")
            .append("        borderColor: '#dc3545',\n")
            .append("        backgroundColor: 'rgba(220, 53, 69, 0.1)',\n")
            .append("        fill: true\n")
            .append("      }]\n")
            .append("    },\n")
            .append("    options: {\n")
            .append("      responsive: true,\n")
            .append("      maintainAspectRatio: false,\n")
            .append("      scales: {\n")
            .append("        y: {\n")
            .append("          beginAtZero: true,\n")
            .append("          ticks: {\n")
            .append("            precision: 0\n")
            .append("          }\n")
            .append("        }\n")
            .append("      }\n")
            .append("    }\n")
            .append("  });\n")
            .append("});\n")
            .append("</script>\n");

        html.append("</body>\n</html>");

        return html.toString();
    }

    /**
     * Escape a string for HTML
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * Generate Excel report with multiple sheets
     * @param reportDir Directory to save the report
     * @param resultReport Test result report data
     * @param coverageReport Coverage report data
     */
    private void generateExcelReport(Path reportDir, TestResultReport resultReport,
            CoverageReport coverageReport) throws IOException {
        logger.info("Generating Excel report");

        // Create workbook
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Create styles
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            XSSFCellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 1. Summary sheet
            XSSFSheet summarySheet = workbook.createSheet("Summary");
            createSummarySheet(summarySheet, headerStyle, dataStyle, resultReport, coverageReport);

            // 2. Test Details sheet
            XSSFSheet testDetailsSheet = workbook.createSheet("Test Details");
            createTestDetailsSheet(testDetailsSheet, headerStyle, dataStyle);

            // 3. Coverage sheet
            XSSFSheet coverageSheet = workbook.createSheet("Coverage");
            createCoverageSheet(coverageSheet, headerStyle, dataStyle, coverageReport);

            // 4. Failure Patterns sheet
            XSSFSheet failurePatternsSheet = workbook.createSheet("Failure Patterns");
            createFailurePatternsSheet(failurePatternsSheet, headerStyle, dataStyle, resultReport);

            // Auto-size columns for all sheets
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                for (int j = 0; j < 10; j++) { // Adjust max columns as needed
                    sheet.autoSizeColumn(j);
                }
            }

            // Write the workbook to file
            try (FileOutputStream fileOut = new FileOutputStream(reportDir.resolve("test-report.xlsx").toFile())) {
                workbook.write(fileOut);
            }

            logger.info("Excel report generated successfully");
        }
    }

    /**
     * Create the summary sheet in the Excel report
     */
    private void createSummarySheet(XSSFSheet sheet, XSSFCellStyle headerStyle, XSSFCellStyle dataStyle,
            TestResultReport resultReport, CoverageReport coverageReport) {
        // Create title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Test Execution Summary");

        // Basic statistics
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Metric", "Value"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 3;

        // Add summary data
        addSummaryRow(sheet, rowNum++, dataStyle, "Total Tests", resultReport.getTotalTests());
        addSummaryRow(sheet, rowNum++, dataStyle, "Passed Tests", resultReport.getPassedTests());
        addSummaryRow(sheet, rowNum++, dataStyle, "Failed Tests", resultReport.getFailedTests());
        addSummaryRow(sheet, rowNum++, dataStyle, "Broken Tests", resultReport.getBrokenTests());
        addSummaryRow(sheet, rowNum++, dataStyle, "Healed Tests", resultReport.getHealedTests());
        addSummaryRow(sheet, rowNum++, dataStyle, "Success Rate",
                String.format("%.2f%%", resultReport.getSuccessRate()));
        addSummaryRow(sheet, rowNum++, dataStyle, "Average Execution Time",
                resultReport.getAverageExecutionTimeMs() + " ms");
        addSummaryRow(sheet, rowNum++, dataStyle, "Code Coverage",
                String.format("%.2f%%", coverageReport.getCoveragePercentage()));
        addSummaryRow(sheet, rowNum++, dataStyle, "Methods Covered",
                coverageReport.getCoveredMethods() + " of " + coverageReport.getTotalMethods());

        // Add report generation info
        rowNum += 2;
        Row generatedRow = sheet.createRow(rowNum++);
        Cell genLabelCell = generatedRow.createCell(0);
        genLabelCell.setCellValue("Report Generated");
        genLabelCell.setCellStyle(dataStyle);

        Cell genValueCell = generatedRow.createCell(1);
        genValueCell.setCellValue(resultReport.getGeneratedAt().toString());
        genValueCell.setCellStyle(dataStyle);
    }

    /**
     * Add a row to the summary sheet
     */
    private void addSummaryRow(XSSFSheet sheet, int rowNum, XSSFCellStyle style, String label, Object value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);

        Cell valueCell = row.createCell(1);
        if (value instanceof Number) {
            valueCell.setCellValue(((Number)value).doubleValue());
        } else {
            valueCell.setCellValue(value.toString());
        }
        valueCell.setCellStyle(style);
    }

    /**
     * Create the test details sheet in the Excel report
     */
    private void createTestDetailsSheet(XSSFSheet sheet, XSSFCellStyle headerStyle, XSSFCellStyle dataStyle) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "Name", "Class", "Method", "Type", "Status", "Last Executed",
                "Execution Time (ms)", "Error Message"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Get all tests
        List<TestCase> allTests = testCaseRepository.findAll();

        // Add test data
        int rowNum = 1;
        for (TestCase test : allTests) {
            Row row = sheet.createRow(rowNum++);

            // ID
            Cell cell0 = row.createCell(0);
            cell0.setCellValue(test.getId().toString());
            cell0.setCellStyle(dataStyle);

            // Name
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(test.getName());
            cell1.setCellStyle(dataStyle);

            // Class
            Cell cell2 = row.createCell(2);
            cell2.setCellValue(test.getClassName());
            cell2.setCellStyle(dataStyle);

            // Method
            Cell cell3 = row.createCell(3);
            cell3.setCellValue(test.getMethodName());
            cell3.setCellStyle(dataStyle);

            // Type
            Cell cell4 = row.createCell(4);
            cell4.setCellValue(test.getType().toString());
            cell4.setCellStyle(dataStyle);

            // Status
            Cell cell5 = row.createCell(5);
            cell5.setCellValue(test.getStatus().toString());
            cell5.setCellStyle(dataStyle);

            // Last Executed
            Cell cell6 = row.createCell(6);
            cell6.setCellValue(test.getLastExecutedAt() != null ? test.getLastExecutedAt().toString() : "");
            cell6.setCellStyle(dataStyle);

            // Execution Time
            Cell cell7 = row.createCell(7);
            if (test.getLastExecutionResult() != null) {
                cell7.setCellValue(test.getLastExecutionResult().getExecutionTimeMs());
            } else {
                cell7.setCellValue("");
            }
            cell7.setCellStyle(dataStyle);

            // Error Message
            Cell cell8 = row.createCell(8);
            if (test.getLastExecutionResult() != null && test.getLastExecutionResult().getErrorMessage() != null) {
                cell8.setCellValue(test.getLastExecutionResult().getErrorMessage());
            } else {
                cell8.setCellValue("");
            }
            cell8.setCellStyle(dataStyle);
        }
    }

    /**
     * Create the coverage sheet in the Excel report
     */
    private void createCoverageSheet(XSSFSheet sheet, XSSFCellStyle headerStyle, XSSFCellStyle dataStyle,
            CoverageReport coverageReport) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Package", "Class", "Methods Covered", "Total Methods", "Coverage %"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;

        // Add package coverage data
        if (coverageReport.getPackageCoverage() != null) {
            for (TestCoverageCalculator.PackageCoverage pkg : coverageReport.getPackageCoverage()) {
                // Package summary row
                Row packageRow = sheet.createRow(rowNum++);

                Cell pkgNameCell = packageRow.createCell(0);
                pkgNameCell.setCellValue(pkg.getPackageName());
                pkgNameCell.setCellStyle(dataStyle);

                Cell pkgClassCell = packageRow.createCell(1);
                pkgClassCell.setCellValue(""); // No class for package summary
                pkgClassCell.setCellStyle(dataStyle);

                Cell pkgCoveredCell = packageRow.createCell(2);
                pkgCoveredCell.setCellValue(pkg.getCoveredMethods());
                pkgCoveredCell.setCellStyle(dataStyle);

                Cell pkgTotalCell = packageRow.createCell(3);
                pkgTotalCell.setCellValue(pkg.getTotalMethods());
                pkgTotalCell.setCellStyle(dataStyle);

                Cell pkgCoverageCell = packageRow.createCell(4);
                pkgCoverageCell.setCellValue(pkg.getCoveragePercentage());
                pkgCoverageCell.setCellStyle(dataStyle);

                // Class coverage rows
                for (Map.Entry<String, TestCoverageCalculator.ClassCoverage> classEntry :
                        pkg.getClassCoverage().entrySet()) {

                    TestCoverageCalculator.ClassCoverage classCov = classEntry.getValue();

                    Row classRow = sheet.createRow(rowNum++);

                    Cell classNameCell1 = classRow.createCell(0);
                    classNameCell1.setCellValue(""); // No package for class detail
                    classNameCell1.setCellStyle(dataStyle);

                    Cell classNameCell2 = classRow.createCell(1);
                    classNameCell2.setCellValue(classCov.getClassName());
                    classNameCell2.setCellStyle(dataStyle);

                    Cell classCoveredCell = classRow.createCell(2);
                    classCoveredCell.setCellValue(classCov.getCoveredMethods());
                    classCoveredCell.setCellStyle(dataStyle);

                    Cell classTotalCell = classRow.createCell(3);
                    classTotalCell.setCellValue(classCov.getTotalMethods());
                    classTotalCell.setCellStyle(dataStyle);

                    Cell classCoverageCell = classRow.createCell(4);
                    classCoverageCell.setCellValue(classCov.getCoveragePercentage());
                    classCoverageCell.setCellStyle(dataStyle);
                }

                // Add an empty row between packages
                rowNum++;
            }
        }
    }

    /**
     * Create the failure patterns sheet in the Excel report
     */
    private void createFailurePatternsSheet(XSSFSheet sheet, XSSFCellStyle headerStyle, XSSFCellStyle dataStyle,
            TestResultReport resultReport) {
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Error Pattern", "Occurrences", "Affected Tests"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;

        // Add failure pattern data
        if (resultReport.getTopFailurePatterns() != null) {
            for (TestResultAnalyzer.FailurePattern pattern : resultReport.getTopFailurePatterns()) {
                Row row = sheet.createRow(rowNum++);

                Cell patternCell = row.createCell(0);
                patternCell.setCellValue(pattern.getPattern());
                patternCell.setCellStyle(dataStyle);

                Cell occurrencesCell = row.createCell(1);
                occurrencesCell.setCellValue(pattern.getOccurrences());
                occurrencesCell.setCellStyle(dataStyle);

                // Convert list of test cases to string of test names
                List<String> testNames = pattern.getAffectedTests().stream()
                                                .map(TestCase::getName)
                                                .collect(Collectors.toList());

                Cell testsCell = row.createCell(2);
                testsCell.setCellValue(String.join(", ", testNames));
                testsCell.setCellStyle(dataStyle);
            }
        }
    }
}