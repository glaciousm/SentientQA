package com.projectoracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for Project Oracle - AI-Powered QA Platform
 * This application provides automated test generation, execution, and analysis
 * using local AI models without cloud dependencies.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ProjectOracleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectOracleApplication.class, args);
        System.out.println("Project Oracle initialized - AI-Powered QA Platform");
        System.out.println("Running with local models - no cloud dependencies");
    }
}