# Project Oracle

An AI-powered QA platform that operates locally with free, open-source models.

## Overview

Project Oracle is a Java-based application that uses local AI models to automate test generation, execution, and analysis. The platform aims to reduce the manual effort required for software testing while improving test coverage and quality.

## Features

- **Code Analysis**: Automatically analyze Java code to extract structural information
- **AI-Powered Test Generation**: Generate test cases using local language models
- **Resource-Optimized**: Designed to run efficiently on standard PC hardware
- **No Cloud Dependencies**: All AI processing happens locally with free models

## Requirements

- Java 21 or higher
- Maven 3.8 or higher
- At least 4GB of RAM (8GB+ recommended)
- 50GB of disk space for models and data

## Quick Start

1. Clone the repository
2. Build the project: `mvn clean install`
3. Run the application: `java -jar target/project-oracle-0.1.0-SNAPSHOT.jar`
4. Access the API at http://localhost:8080/api/v1/

## API Endpoints

- `GET /api/v1/health` - Health check
- `POST /api/v1/analyze/code` - Analyze Java code
- `GET /api/v1/analyze/file` - Analyze a Java file
- `GET /api/v1/analyze/directory` - Scan a directory for Java files
- `POST /api/v1/generate/test` - Generate a test for a method
- `POST /api/v1/generate/tests/file` - Generate tests for all methods in a file

## Configuration

See `src/main/resources/ai-config.properties` for AI model configuration options.

## License

This project is open source and available under the MIT License.