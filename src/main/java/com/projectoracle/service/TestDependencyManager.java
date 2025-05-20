package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Service for managing test dependencies.
 * Ensures required libraries and resources are available for test compilation and execution.
 */
@Service
public class TestDependencyManager {

    private static final Logger logger = LoggerFactory.getLogger(TestDependencyManager.class);

    @Value("${app.directories.output:output}")
    private String outputDir;

    // List of required dependencies for tests
    private static final String[] REQUIRED_DEPENDENCIES = {
            "junit-jupiter-api",
            "junit-jupiter-engine",
            "junit-platform-launcher",
            "selenium-java",
            "selenium-api",
            "selenium-chrome-driver",
            "selenium-support"
    };

    // Cache of resolved dependency paths
    private final Set<String> resolvedDependencies = ConcurrentHashMap.newKeySet();

    /**
     * Initialize the test environment with required dependencies
     */
    public void initializeTestEnvironment() {
        logger.info("Initializing test environment");

        try {
            // Create output directories
            Path classesDir = Paths.get(outputDir, "classes");
            Path libDir = Paths.get(outputDir, "lib");

            Files.createDirectories(classesDir);
            Files.createDirectories(libDir);

            // Resolve and copy dependencies
            for (String dependency : REQUIRED_DEPENDENCIES) {
                copyDependencyToLib(dependency, libDir);
            }

            logger.info("Test environment initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize test environment", e);
            throw new RuntimeException("Failed to initialize test environment", e);
        }
    }

    /**
     * Copy a dependency to the lib directory
     */
    private void copyDependencyToLib(String dependencyName, Path libDir) throws IOException {
        logger.info("Resolving dependency: {}", dependencyName);

        // Find JAR files on classpath
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:**/" + dependencyName + "*.jar");

        if (resources.length == 0) {
            logger.warn("Dependency not found: {}", dependencyName);
            return;
        }

        // Copy the first matching JAR
        Resource jarResource = resources[0];
        String jarName = jarResource.getFilename();

        if (jarName != null) {
            Path targetPath = libDir.resolve(jarName);

            // Copy only if the file doesn't exist
            if (!Files.exists(targetPath)) {
                Files.copy(jarResource.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied dependency {} to {}", jarName, targetPath);
            }

            resolvedDependencies.add(targetPath.toString());
        }
    }

    /**
     * Get the classpath for test compilation and execution
     */
    public String getTestClasspath() {
        List<String> classpathElements = new ArrayList<>(resolvedDependencies);

        // Add classes directory
        classpathElements.add(Paths.get(outputDir, "classes").toString());

        // Join with path separator
        return String.join(File.pathSeparator, classpathElements);
    }

    /**
     * Extract additional test resources from JARs
     * (e.g., config files, properties, etc.)
     */
    public void extractTestResources() {
        logger.info("Extracting test resources");

        try {
            Path resourcesDir = Paths.get(outputDir, "resources");
            Files.createDirectories(resourcesDir);

            // Process each dependency JAR
            for (String dependencyPath : resolvedDependencies) {
                File jarFile = new File(dependencyPath);

                try (JarFile jar = new JarFile(jarFile)) {
                    // Find resource entries
                    jar.stream()
                       .filter(entry -> !entry.isDirectory() && isResourceFile(entry.getName()))
                       .forEach(entry -> extractResource(jar, entry, resourcesDir));
                } catch (IOException e) {
                    logger.warn("Failed to process JAR: {}", jarFile, e);
                }
            }

            logger.info("Test resources extracted successfully");
        } catch (IOException e) {
            logger.error("Failed to extract test resources", e);
        }
    }

    /**
     * Check if a file is a resource file that should be extracted
     */
    private boolean isResourceFile(String name) {
        return name.endsWith(".properties") ||
                name.endsWith(".xml") ||
                name.endsWith(".yaml") ||
                name.endsWith(".yml") ||
                name.endsWith(".json");
    }

    /**
     * Extract a resource from a JAR file
     */
    private void extractResource(JarFile jar, ZipEntry entry, Path targetDir) {
        try {
            // Get resource name
            String name = entry.getName();
            if (name.contains("/")) {
                name = name.substring(name.lastIndexOf('/') + 1);
            }

            // Create target path
            Path targetPath = targetDir.resolve(name);

            // Extract resource
            Files.copy(jar.getInputStream(entry), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Extracted resource: {}", name);
        } catch (IOException e) {
            logger.warn("Failed to extract resource: {}", entry.getName(), e);
        }
    }
}