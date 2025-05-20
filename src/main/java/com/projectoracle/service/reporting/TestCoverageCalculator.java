package com.projectoracle.service.reporting;

import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.service.CodeAnalysisService;
import com.projectoracle.service.MethodInfo;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating test coverage metrics.
 * Analyzes how well the codebase is covered by tests.
 */
@Service
public class TestCoverageCalculator {

    private static final Logger logger = LoggerFactory.getLogger(TestCoverageCalculator.class);

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private CodeAnalysisService codeAnalysisService;

    @Value("${app.directories.source:src/main/java}")
    private String sourceDir;

    /**
     * Calculate overall coverage as a percentage
     * 
     * @return Overall code coverage percentage (0-100)
     */
    public double calculateOverallCoverage() {
        CoverageReport report = calculateCoverage();
        return report.getCoveragePercentage();
    }
    
    /**
     * Calculate test coverage for a source directory
     * 
     * @return Detailed coverage report
     */
    public CoverageReport calculateCoverage() {
        logger.info("Calculating test coverage for source directory: {}", sourceDir);

        CoverageReport report = new CoverageReport();
        report.setSourceDirectory(sourceDir);

        try {
            // Get all test cases
            List<TestCase> allTests = testCaseRepository.findAll();
            report.setTotalTests(allTests.size());

            // Get all methods in the source directory
            List<MethodInfo> allMethods = codeAnalysisService.scanDirectory(Paths.get(sourceDir));
            report.setTotalMethods(allMethods.size());

            // Map methods by package, class, and method name
            Map<String, Set<String>> testedMethods = new HashMap<>();

            // Find which methods are covered by tests
            for (TestCase test : allTests) {
                if (test.getMethodName() != null && test.getClassName() != null) {
                    // Extract target method from test name
                    String targetMethod = extractTargetMethodFromTestName(test.getMethodName());
                    String targetClass = test.getClassName().replace("Test", "");

                    // Store as covered
                    testedMethods.computeIfAbsent(targetClass, k -> new HashSet<>()).add(targetMethod);
                }
            }

            // Calculate coverage
            int coveredMethods = 0;
            Map<String, PackageCoverage> packageCoverageMap = new HashMap<>();

            for (MethodInfo method : allMethods) {
                String className = method.getClassName();
                String methodName = method.getMethodName();
                String packageName = method.getPackageName();

                // Skip test classes
                if (className.endsWith("Test")) {
                    continue;
                }

                // Check if method is tested
                boolean isTested = testedMethods.containsKey(className) &&
                        testedMethods.get(className).contains(methodName);

                if (isTested) {
                    coveredMethods++;
                }

                // Update package coverage
                PackageCoverage packageCoverage = packageCoverageMap.computeIfAbsent(
                        packageName, k -> new PackageCoverage(packageName));

                packageCoverage.incrementTotal();
                if (isTested) {
                    packageCoverage.incrementCovered();
                }

                // Update class coverage
                ClassCoverage classCoverage = packageCoverage.getClassCoverage().computeIfAbsent(
                        className, k -> new ClassCoverage(className));

                classCoverage.incrementTotal();
                if (isTested) {
                    classCoverage.incrementCovered();
                }

                // Add method info
                MethodCoverage methodCoverage = new MethodCoverage(methodName, methodName, isTested);
                classCoverage.getMethods().add(methodCoverage);
            }

            // Calculate overall coverage percentage
            double coveragePercentage = allMethods.isEmpty() ? 0 :
                    (double) coveredMethods / allMethods.size() * 100;
            report.setCoveragePercentage(Math.round(coveragePercentage * 100.0) / 100.0);
            report.setCoveredMethods(coveredMethods);

            // Set package coverage data
            List<PackageCoverage> packageCoverage = new ArrayList<>(packageCoverageMap.values());
            packageCoverage.sort(Comparator.comparing(PackageCoverage::getPackageName));
            report.setPackageCoverage(packageCoverage);

            logger.info("Coverage calculation completed: {}% coverage ({}/{} methods)",
                    report.getCoveragePercentage(), coveredMethods, allMethods.size());

            return report;

        } catch (Exception e) {
            logger.error("Error calculating test coverage", e);
            report.setError("Error calculating coverage: " + e.getMessage());
            return report;
        }
    }

    /**
     * Extract target method name from test method name
     */
    private String extractTargetMethodFromTestName(String testMethodName) {
        if (testMethodName.startsWith("test")) {
            // Remove "test" prefix and extract method name
            String nameWithoutPrefix = testMethodName.substring(4);

            // Handle various test naming patterns
            int underscoreIndex = nameWithoutPrefix.indexOf('_');
            if (underscoreIndex > 0) {
                // For names like "testMethod_scenario"
                return lcFirst(nameWithoutPrefix.substring(0, underscoreIndex));
            } else {
                // For names like "testMethod"
                return lcFirst(nameWithoutPrefix);
            }
        }

        return testMethodName;
    }

    /**
     * Convert first letter to lowercase
     */
    private String lcFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Coverage report class
     */
    @Data
    public static class CoverageReport {
        private String sourceDirectory;
        private int totalMethods;
        private int coveredMethods;
        private double coveragePercentage;
        private int totalTests;
        private List<PackageCoverage> packageCoverage;
        private String error;
    }

    /**
     * Package coverage class
     */
    public static class PackageCoverage {
        private String packageName;
        private int totalMethods;
        private int coveredMethods;
        private Map<String, ClassCoverage> classCoverage = new HashMap<>();

        public PackageCoverage(String packageName) {
            this.packageName = packageName;
        }

        public void incrementTotal() {
            totalMethods++;
        }

        public void incrementCovered() {
            coveredMethods++;
        }

        public String getPackageName() {
            return packageName;
        }

        public int getTotalMethods() {
            return totalMethods;
        }

        public int getCoveredMethods() {
            return coveredMethods;
        }

        public double getCoveragePercentage() {
            return totalMethods == 0 ? 0 : (double) coveredMethods / totalMethods * 100;
        }

        public Map<String, ClassCoverage> getClassCoverage() {
            return classCoverage;
        }
    }

    /**
     * Class coverage class
     */
    public static class ClassCoverage {
        private String className;
        private int totalMethods;
        private int coveredMethods;
        private List<MethodCoverage> methods = new ArrayList<>();

        public ClassCoverage(String className) {
            this.className = className;
        }

        public void incrementTotal() {
            totalMethods++;
        }

        public void incrementCovered() {
            coveredMethods++;
        }

        public String getClassName() {
            return className;
        }

        public int getTotalMethods() {
            return totalMethods;
        }

        public int getCoveredMethods() {
            return coveredMethods;
        }

        public double getCoveragePercentage() {
            return totalMethods == 0 ? 0 : (double) coveredMethods / totalMethods * 100;
        }

        public List<MethodCoverage> getMethods() {
            return methods;
        }
    }

    /**
     * Method coverage class
     */
    public static class MethodCoverage {
        private String methodName;
        private String signature;
        private boolean covered;

        public MethodCoverage(String methodName, String signature, boolean covered) {
            this.methodName = methodName;
            this.signature = signature;
            this.covered = covered;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getSignature() {
            return signature;
        }

        public boolean isCovered() {
            return covered;
        }
    }
}