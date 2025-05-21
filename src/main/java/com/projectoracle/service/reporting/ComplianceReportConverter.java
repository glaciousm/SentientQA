package com.projectoracle.service.reporting;

import com.projectoracle.service.ComplianceTestingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to convert between different ComplianceReport formats
 */
public class ComplianceReportConverter {

    /**
     * Convert from ComplianceTestingService.ComplianceReport to ComplianceReportGenerator.ComplianceReport
     *
     * @param source The source ComplianceReport from ComplianceTestingService
     * @return The converted ComplianceReport for ComplianceReportGenerator
     */
    public static ComplianceReportGenerator.ComplianceReport convert(
            ComplianceTestingService.ComplianceReport source) {
        
        ComplianceReportGenerator.ComplianceReport target = new ComplianceReportGenerator.ComplianceReport();
        
        // Set fields that exist in both classes
        target.setClassName(source.getClassName());
        target.setStandard(source.getStandard().name());
        target.setCompliancePercentage(source.getCompliancePercentage());
        target.setTestedAt(source.getTestedAt());
        
        // Convert violations if they exist
        if (source.getViolations() != null) {
            List<ComplianceReportGenerator.ComplianceViolation> violations = source.getViolations().stream()
                .map(ComplianceReportConverter::convert)
                .collect(Collectors.toList());
            target.setViolations(violations);
        }
        
        return target;
    }
    
    /**
     * Convert from ComplianceTestingService.ComplianceViolation to ComplianceReportGenerator.ComplianceViolation
     *
     * @param source The source ComplianceViolation from ComplianceTestingService
     * @return The converted ComplianceViolation for ComplianceReportGenerator
     */
    public static ComplianceReportGenerator.ComplianceViolation convert(
            ComplianceTestingService.ComplianceViolation source) {
        
        ComplianceReportGenerator.ComplianceViolation target = new ComplianceReportGenerator.ComplianceViolation();
        
        // Set fields that exist in both classes
        target.setRuleId(source.getRuleId());
        target.setRuleName(source.getRuleName());
        target.setSeverity(ComplianceReportGenerator.ComplianceSeverity.valueOf(source.getSeverity().name()));
        target.setClassName(source.getClassName());
        target.setMethodName(source.getMethodName());
        target.setDescription(source.getDescription());
        target.setDetectionPattern(source.getDetectionPattern());
        target.setRemediationSteps(source.getRemediationSteps());
        target.setAiAnalysis(source.getAiAnalysis());
        
        return target;
    }
    
    /**
     * Convert a map of ComplianceTestingService.ComplianceStandard to ComplianceTestingService.ComplianceReport
     * into a Map<String, ComplianceReportGenerator.ComplianceReport>
     *
     * @param sourceMap The source map from ComplianceTestingService
     * @return The converted map for ComplianceReportGenerator
     */
    public static Map<String, ComplianceReportGenerator.ComplianceReport> convertMap(
            Map<ComplianceTestingService.ComplianceStandard, ComplianceTestingService.ComplianceReport> sourceMap) {
        
        Map<String, ComplianceReportGenerator.ComplianceReport> targetMap = new HashMap<>();
        
        sourceMap.forEach((standard, report) -> {
            targetMap.put(standard.name(), convert(report));
        });
        
        return targetMap;
    }
}