package com.projectoracle.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for analyzing Java code to extract information needed for test generation.
 * Uses JavaParser to build abstract syntax trees and extract structural information.
 */
@Service
public class CodeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(CodeAnalysisService.class);
    
    @Value("${crawler.code-analysis.enabled:false}")
    private boolean codeAnalysisEnabled;
    private final JavaParser javaParser = new JavaParser();

    /**
     * Analyzes a Java source file and extracts method information
     *
     * @param filePath path to the Java source file
     * @return map of method names to their details
     */
    public Map<String, MethodInfo> analyzeJavaFile(Path filePath) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - skipping file: {}", filePath);
            return Map.of();
        }
        
        try {
            logger.info("Analyzing Java file: {}", filePath);
            String sourceCode = Files.readString(filePath);
            return analyzeJavaSource(sourceCode);
        } catch (IOException e) {
            logger.error("Failed to read Java file: {}", filePath, e);
            return Map.of();
        }
    }

    /**
     * Analyzes Java source code as a string
     *
     * @param sourceCode Java source code
     * @return map of method names to their details
     */
    public Map<String, MethodInfo> analyzeJavaSource(String sourceCode) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - skipping source analysis");
            return Map.of();
        }
        
        Map<String, MethodInfo> methodInfoMap = new HashMap<>();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();

                // Extract package name
                String packageName = cu.getPackageDeclaration()
                                       .map(pd -> pd.getName().asString())
                                       .orElse("");

                // Visit all classes and methods
                MethodVisitor methodVisitor = new MethodVisitor(packageName);
                cu.accept(methodVisitor, null);

                methodInfoMap = methodVisitor.getMethodInfoMap();
                logger.info("Found {} methods in source code", methodInfoMap.size());
            } else {
                logger.error("Failed to parse Java source code");
            }
        } catch (Exception e) {
            logger.error("Error analyzing Java source", e);
        }

        return methodInfoMap;
    }

    /**
     * Scan a directory for Java files and analyze them
     *
     * @param dirPath path to the directory to scan
     * @return list of method information for all Java files
     */
    public List<MethodInfo> scanDirectory(Path dirPath) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - skipping directory scan: {}", dirPath);
            return List.of();
        }
        
        try (Stream<Path> paths = Files.walk(dirPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> analyzeJavaFile(path).values().stream())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to scan directory: {}", dirPath, e);
            return List.of();
        }
    }
    
    /**
     * Find a method by its signature
     *
     * @param methodSignature The signature of the method to find (e.g., "calculateTotal(int, int)")
     * @return MethodInfo for the requested method, or null if not found
     */
    public MethodInfo findMethodBySignature(String methodSignature) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - cannot find method: {}", methodSignature);
            return null;
        }
        
        logger.info("Looking for method with signature: {}", methodSignature);
        
        // Extract method name from signature
        String methodName = methodSignature;
        if (methodSignature.contains("(")) {
            methodName = methodSignature.substring(0, methodSignature.indexOf('('));
        }
        
        // Search in all Java files in the current directory
        try (Stream<Path> paths = Files.walk(Path.of("."), 10)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                Map<String, MethodInfo> methodInfos = analyzeJavaFile(file);
                
                // Look for exact signature match first
                for (MethodInfo methodInfo : methodInfos.values()) {
                    if (methodInfo.getSignature().equals(methodSignature) ||
                        methodInfo.getMethodName().equals(methodName)) {
                        logger.info("Found method {} in {}", methodSignature, file);
                        return methodInfo;
                    }
                }
            }
            
            logger.warn("Method with signature {} not found", methodSignature);
            return null;
        } catch (IOException e) {
            logger.error("Error searching for method: {}", methodSignature, e);
            return null;
        }
    }
    
    /**
     * Find all methods in a specific class
     *
     * @param className The name of the class to search for methods
     * @return List of MethodInfo objects for all methods in the class
     */
    public List<MethodInfo> findMethodsByClassName(String className) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - cannot find methods in class: {}", className);
            return List.of();
        }
        
        logger.info("Looking for methods in class: {}", className);
        
        List<MethodInfo> methods = new ArrayList<>();
        
        // Search in all Java files in the current directory
        try (Stream<Path> paths = Files.walk(Path.of("."), 10)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                Map<String, MethodInfo> methodInfos = analyzeJavaFile(file);
                
                // Check each method's class name
                for (MethodInfo methodInfo : methodInfos.values()) {
                    if (methodInfo.getClassName().equals(className)) {
                        methods.add(methodInfo);
                    }
                }
            }
            
            if (methods.isEmpty()) {
                logger.warn("No methods found for class {}", className);
            } else {
                logger.info("Found {} methods in class {}", methods.size(), className);
            }
            
            return methods;
        } catch (IOException e) {
            logger.error("Error searching for methods in class: {}", className, e);
            return List.of();
        }
    }
    
    /**
     * Find a specific method by class and method name
     *
     * @param className The name of the class
     * @param methodName The name of the method
     * @return MethodInfo for the requested method, or null if not found
     */
    public MethodInfo findMethodByClassAndName(String className, String methodName) {
        if (!codeAnalysisEnabled) {
            logger.info("🚫 Code analysis is DISABLED - cannot find method {} in class {}", methodName, className);
            return null;
        }
        
        logger.info("Looking for method {} in class {}", methodName, className);
        
        // Search in all Java files in the current directory
        try (Stream<Path> paths = Files.walk(Path.of("."), 10)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                Map<String, MethodInfo> methodInfos = analyzeJavaFile(file);
                
                // Check each method's class and method name
                for (MethodInfo methodInfo : methodInfos.values()) {
                    if (methodInfo.getClassName().equals(className) &&
                        methodInfo.getMethodName().equals(methodName)) {
                        logger.info("Found method {} in class {}", methodName, className);
                        return methodInfo;
                    }
                }
            }
            
            logger.warn("Method {} not found in class {}", methodName, className);
            return null;
        } catch (IOException e) {
            logger.error("Error searching for method {} in class {}", methodName, className, e);
            return null;
        }
    }

    /**
     * Visitor class that extracts method information from Java AST
     */
    private static class MethodVisitor extends VoidVisitorAdapter<Void> {
        private final String packageName;
        private final Map<String, MethodInfo> methodInfoMap = new HashMap<>();
        private String currentClassName = "";

        public MethodVisitor(String packageName) {
            this.packageName = packageName;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Store current class name
            currentClassName = classDecl.getNameAsString();
            super.visit(classDecl, arg);
        }

        @Override
        public void visit(MethodDeclaration methodDecl, Void arg) {
            // Extract method information
            String methodName = methodDecl.getNameAsString();
            String returnType = methodDecl.getType().asString();

            // Create a unique key for this method
            String methodKey = currentClassName + "." + methodName;

            // Extract parameters
            List<ParameterInfo> parameters = methodDecl.getParameters().stream()
                                                       .map(p -> new ParameterInfo(p.getType().asString(), p.getNameAsString()))
                                                       .collect(Collectors.toList());

            // Extract exceptions
            List<String> exceptions = methodDecl.getThrownExceptions().stream()
                                                .map(Object::toString)
                                                .collect(Collectors.toList());

            // Extract Javadoc if present
            String javadoc = methodDecl.getJavadoc()
                                       .map(doc -> doc.getDescription().toText())
                                       .orElse("");

            // Build method info
            MethodInfo methodInfo = MethodInfo.builder()
                                              .packageName(packageName)
                                              .className(currentClassName)
                                              .methodName(methodName)
                                              .returnType(returnType)
                                              .parameters(parameters)
                                              .exceptions(exceptions)
                                              .javadoc(javadoc)
                                              .isPublic(methodDecl.isPublic())
                                              .isStatic(methodDecl.isStatic())
                                              .body(methodDecl.getBody().map(Object::toString).orElse(""))
                                              .build();

            // Store in map
            methodInfoMap.put(methodKey, methodInfo);

            super.visit(methodDecl, arg);
        }

        public Map<String, MethodInfo> getMethodInfoMap() {
            return methodInfoMap;
        }
    }
}