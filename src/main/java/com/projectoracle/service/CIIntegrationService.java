package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service that generates CI/CD configuration files for Jenkins and GitHub Actions.
 * These basic pipelines simply build the project and run the generated tests.
 */
@Service
public class CIIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(CIIntegrationService.class);

    /**
     * Create a simple Jenkinsfile that performs a Maven build and archives results.
     *
     * @param projectDir the project directory where the Jenkinsfile should be created
     */
    public void createJenkinsfile(String projectDir) {
        Path jenkinsfile = Paths.get(projectDir, "Jenkinsfile");
        String content = "pipeline {\n" +
                "    agent any\n" +
                "    stages {\n" +
                "        stage('Build') {\n" +
                "            steps {\n" +
                "                sh 'mvn -B clean install'\n" +
                "            }\n" +
                "        }\n" +
                "        stage('Docker') {\n" +
                "            steps {\n" +
                "                sh 'docker build -t sentinel .'\n" +
                "            }\n" +
                "        }\n" +
                "        stage('Archive Results') {\n" +
                "            steps {\n" +
                "                junit 'target/surefire-reports/*.xml'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        try {
            Files.writeString(jenkinsfile, content);
            logger.info("Created Jenkinsfile at {}", jenkinsfile);
        } catch (IOException e) {
            logger.error("Failed to create Jenkinsfile", e);
        }
    }

    /**
     * Create a basic GitHub Actions workflow for Maven.
     *
     * @param projectDir the project directory where the workflow should be placed
     */
    public void createGitHubActionsWorkflow(String projectDir) {
        Path workflowDir = Paths.get(projectDir, ".github", "workflows");
        Path workflowFile = workflowDir.resolve("ci.yml");
        String content = "name: CI\n" +
                "on: [push, pull_request]\n" +
                "jobs:\n" +
                "  build:\n" +
                "    runs-on: ubuntu-latest\n" +
                "    steps:\n" +
                "      - uses: actions/checkout@v4\n" +
                "      - name: Set up JDK\n" +
                "        uses: actions/setup-java@v4\n" +
                "        with:\n" +
                "          java-version: '21'\n" +
                "          distribution: 'temurin'\n" +
                "      - name: Build\n" +
                "        run: mvn -B clean install\n" +
                "      - name: Build Docker image\n" +
                "        run: docker build -t sentinel .\n";
        try {
            Files.createDirectories(workflowDir);
            Files.writeString(workflowFile, content);
            logger.info("Created GitHub Actions workflow at {}", workflowFile);
        } catch (IOException e) {
            logger.error("Failed to create GitHub Actions workflow", e);
        }
    }

    /**
     * Create a simple docker-compose file for running the application.
     *
     * @param projectDir the project directory where the compose file should be created
     */
    public void createDockerComposeFile(String projectDir) {
        Path composeFile = Paths.get(projectDir, "docker-compose.yml");
        String content = "version: '3'\n" +
                "services:\n" +
                "  sentinel:\n" +
                "    build: .\n" +
                "    ports:\n" +
                "      - \"8080:8080\"\n";
        try {
            Files.writeString(composeFile, content);
            logger.info("Created docker-compose.yml at {}", composeFile);
        } catch (IOException e) {
            logger.error("Failed to create docker-compose.yml", e);
        }
    }
}
