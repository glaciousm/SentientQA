package com.projectoracle.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final JavaParser javaParser = new JavaParser();

    /**
     * Analyzes a Java source file and extracts method information
     *
     * @param filePath path to the Java source file
     * @return map of method names to their details
     */
    public Map<String, MethodInfo> analyzeJavaFile(Path filePath) {
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