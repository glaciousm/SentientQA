package com.projectoracle.rest;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.reporting.TestResultAnalyzer;
import com.projectoracle.service.reporting.TestCoverageCalculator;
import com.projectoracle.service.reporting.TestReportGenerator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Controller for report generation and access.
 * Provides endpoints for creating and viewing test reports.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private TestResultAnalyzer resultAnalyzer;

    @Autowired
    private TestCoverageCalculator coverageCalculator;

    @Autowired
    private TestReportGenerator reportGenerator;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Value("${app.directories.output:output}")
    private String outputDir;

    /**
     * Get test result analysis
     */
    @GetMapping("/analyze")
    public ResponseEntity<TestResultAnalyzer.TestResultReport> analyzeTestResults() {
        logger.info("Analyzing test results");

        try {
            TestResultAnalyzer.TestResultReport report = resultAnalyzer.analyzeResults();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Error analyzing test results", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get test coverage analysis
     */
    @GetMapping("/coverage")
    public ResponseEntity<TestCoverageCalculator.CoverageReport> getTestCoverage() {
        logger.info("Calculating test coverage");

        try {
            TestCoverageCalculator.CoverageReport report = coverageCalculator.calculateCoverage();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Error calculating test coverage", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate a comprehensive test report
     */
    @PostMapping("/generate")
    public ResponseEntity<ReportGenerationResponse> generateReport(
            @RequestParam(defaultValue = "true") boolean includeHtml,
            @RequestParam(defaultValue = "true") boolean includeJson,
            @RequestParam(defaultValue = "true") boolean includeCsv,
            @RequestParam(defaultValue = "true") boolean includeExcel) {

        logger.info("Generating test report: html={}, json={}, csv={}, excel={}",
                includeHtml, includeJson, includeCsv, includeExcel);

        try {
            String reportPath = reportGenerator.generateReport();

            return ResponseEntity.ok(new ReportGenerationResponse(
                    "success",
                    "Report generated successfully",
                    reportPath,
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            logger.error("Error generating test report", e);
            return ResponseEntity.internalServerError().body(new ReportGenerationResponse(
                    "error",
                    "Error generating report: " + e.getMessage(),
                    null,
                    LocalDateTime.now()
            ));
        }
    }

    /**
     * Get a list of available reports
     */
    @GetMapping("/list")
    public ResponseEntity<List<ReportInfo>> listReports() {
        logger.info("Listing available reports");

        try {
            Path reportsDir = Paths.get(outputDir, "reports");

            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }

            List<ReportInfo> reports = Files.list(reportsDir)
                                            .filter(Files::isDirectory)
                                            .map(path -> {
                                                String name = path.getFileName().toString();
                                                Path indexHtml = path.resolve("index.html");
                                                boolean hasHtml = Files.exists(indexHtml);

                                                // Parse timestamp from directory name (format: test-report-yyyyMMdd_HHmmss)
                                                LocalDateTime timestamp = null;
                                                if (name.startsWith("test-report-")) {
                                                    try {
                                                        String dateStr = name.substring("test-report-".length());
                                                        timestamp = LocalDateTime.parse(dateStr,
                                                                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                                                    } catch (Exception e) {
                                                        // Ignore parsing error
                                                    }
                                                }

                                                return new ReportInfo(name, path.toString(), hasHtml, timestamp);
                                            })
                                            .sorted((r1, r2) -> {
                                                if (r1.getTimestamp() == null && r2.getTimestamp() == null) {
                                                    return 0;
                                                } else if (r1.getTimestamp() == null) {
                                                    return 1;
                                                } else if (r2.getTimestamp() == null) {
                                                    return -1;
                                                } else {
                                                    return r2.getTimestamp().compareTo(r1.getTimestamp());
                                                }
                                            })
                                            .toList();

            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error listing reports", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get a specific report file
     */
    @GetMapping("/view/{reportId}/{fileName}")
    public ResponseEntity<Resource> getReportFile(
            @PathVariable String reportId,
            @PathVariable String fileName) {

        logger.info("Accessing report file: {}/{}", reportId, fileName);

        try {
            Path reportDir = Paths.get(outputDir, "reports", reportId);
            Path filePath = reportDir.resolve(fileName);

            // Verify the file exists
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            // Make sure the file is within the reports directory (security check)
            if (!filePath.normalize().startsWith(Paths.get(outputDir, "reports").normalize())) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            // Determine content type
            String contentType = determineContentType(fileName);

            return ResponseEntity.ok()
                                 .contentType(MediaType.parseMediaType(contentType))
                                 .header(HttpHeaders.CONTENT_DISPOSITION,
                                         "inline; filename=\"" + fileName + "\"")
                                 .body(resource);

        } catch (Exception e) {
            logger.error("Error accessing report file: {}/{}", reportId, fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Determine content type based on file extension
     */
    private String determineContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html";
        } else if (fileName.endsWith(".json")) {
            return "application/json";
        } else if (fileName.endsWith(".csv")) {
            return "text/csv";
        } else if (fileName.endsWith(".txt")) {
            return "text/plain";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * Response class for report generation
     */
    public static class ReportGenerationResponse {
        private String status;
        private String message;
        private String reportPath;
        private LocalDateTime generatedAt;

        public ReportGenerationResponse(String status, String message, String reportPath,
                LocalDateTime generatedAt) {
            this.status = status;
            this.message = message;
            this.reportPath = reportPath;
            this.generatedAt = generatedAt;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getReportPath() {
            return reportPath;
        }

        public LocalDateTime getGeneratedAt() {
            return generatedAt;
        }
    }

    /**
     * Class representing report information
     */
    @Getter
    @Setter
    public static class ReportInfo {
        private String name;
        private String path;
        private boolean hasHtml;
        private LocalDateTime timestamp;

        public ReportInfo(String name, String path, boolean hasHtml, LocalDateTime timestamp) {
            this.name = name;
            this.path = path;
            this.hasHtml = hasHtml;
            this.timestamp = timestamp;
        }
    }
}