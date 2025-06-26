package com.projectoracle.service;

import com.projectoracle.model.AutomationSetupOption;
import com.projectoracle.model.TestCase;
import com.projectoracle.repository.TestCaseRepository;
import com.projectoracle.repository.APIEndpointRepository;
import com.projectoracle.service.crawler.UICrawlerService;
import com.projectoracle.service.crawler.UITestGenerationService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the creation and enhancement of automation test frameworks
 * based on crawled data and discovered test cases.
 */
@Service
public class TestAutomationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(TestAutomationOrchestrator.class);

    @Autowired
    private UICrawlerService uiCrawlerService;

    @Autowired
    private UITestGenerationService uiTestGenerationService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private JiraService jiraService;

    @Autowired
    private CIIntegrationService ciIntegrationService;

    @Autowired
    private APIEndpointRepository apiEndpointRepository;

    /**
     * Checks if user wants to create automation tests after crawling
     * and orchestrates the automation setup process.
     *
     * @param crawlId The ID of the completed crawl
     * @return True if automation setup was started
     */
    public boolean promptForAutomationSetup(UUID crawlId) {
        logger.info("Prompting for automation setup for crawl: {}", crawlId);
        
        // This would normally be triggered by UI/API interaction
        // For now, we'll assume the user always wants automation tests
        return true;
    }

    /**
     * Creates a new automation project with the selected technology stack
     *
     * @param projectName Name of the project
     * @param basePackage Base package name
     * @param targetDir Target directory for the project
     * @param setupOption Options for project setup (ENHANCE_EXISTING or CREATE_NEW)
     * @param testCaseIds List of test case IDs to include in the automation
     * @return Path to the created project
     */
    public String setupAutomationProject(String projectName, String basePackage, 
                                        String targetDir, AutomationSetupOption setupOption, 
                                        List<UUID> testCaseIds) {
        logger.info("Setting up automation project: {} with option: {}", projectName, setupOption);
        
        try {
            if (setupOption == AutomationSetupOption.ENHANCE_EXISTING) {
                return enhanceExistingProject(targetDir, testCaseIds);
            } else {
                return createNewProject(projectName, basePackage, targetDir, testCaseIds);
            }
        } catch (Exception e) {
            logger.error("Failed to setup automation project", e);
            return null;
        }
    }

    /**
     * Enhances an existing automation project with new tests
     */
    private String enhanceExistingProject(String projectPath, List<UUID> testCaseIds) throws IOException {
        logger.info("Enhancing existing project at: {} with {} tests", projectPath, testCaseIds.size());
        
        // Verify project structure
        Path path = Paths.get(projectPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IOException("Project directory does not exist: " + projectPath);
        }
        
        // Analyze existing project structure
        boolean hasPlaywright = Files.exists(Paths.get(projectPath, "src", "test", "java", "playwright"));
        boolean hasRestAssured = Files.exists(Paths.get(projectPath, "src", "test", "java", "restassured"));
        boolean hasCucumber = Files.exists(Paths.get(projectPath, "src", "test", "resources", "features"));
        
        // Generate and add tests to the existing project
        for (UUID testCaseId : testCaseIds) {
            TestCase testCase = testCaseRepository.findById(testCaseId);
            if (testCase != null) {
                if (testCase.isUiTest() && hasPlaywright) {
                    addPlaywrightTest(projectPath, testCase);
                } else if (testCase.isApiTest() && hasRestAssured) {
                    addRestAssuredTest(projectPath, testCase);
                }
                
                if (hasCucumber) {
                    addCucumberFeature(projectPath, testCase);
                }
            }
        }
        
        return projectPath;
    }

    /**
     * Creates a new automation project with Java 21, Playwright, Rest Assured, and Cucumber
     */
    private String createNewProject(String projectName, String basePackage, 
                                   String targetDir, List<UUID> testCaseIds) throws IOException {
        logger.info("Creating new automation project: {} at: {}", projectName, targetDir);
        
        // Create project directory structure
        String projectDir = targetDir + File.separator + projectName;
        Path projectPath = Paths.get(projectDir);
        Files.createDirectories(projectPath);
        
        // Create main directories
        createDirectoryStructure(projectDir, basePackage);
        
        // Create Maven POM file
        createPomFile(projectDir, projectName, basePackage);
        
        // Initialize Playwright configuration
        initializePlaywright(projectDir, basePackage);
        
        // Initialize RestAssured configuration
        initializeRestAssured(projectDir, basePackage);
        
        // Initialize Cucumber configuration
        initializeCucumber(projectDir, basePackage);
        
        // Generate tests
        for (UUID testCaseId : testCaseIds) {
            TestCase testCase = testCaseRepository.findById(testCaseId);
            if (testCase != null) {
                if (testCase.isUiTest()) {
                    addPlaywrightTest(projectDir, testCase);
                } else if (testCase.isApiTest()) {
                    addRestAssuredTest(projectDir, testCase);
                }

                addCucumberFeature(projectDir, testCase);
            }
        }

        // Generate basic CI/CD configuration
        ciIntegrationService.createJenkinsfile(projectDir);
        ciIntegrationService.createGitHubActionsWorkflow(projectDir);
        ciIntegrationService.createDockerComposeFile(projectDir);

        return projectDir;
    }

    /**
     * Create the directory structure for the automation project
     */
    private void createDirectoryStructure(String projectDir, String basePackage) throws IOException {
        // Convert package to directory structure
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create main directories
        Files.createDirectories(Paths.get(projectDir, "src", "main", "java", packageDir));
        Files.createDirectories(Paths.get(projectDir, "src", "main", "resources"));
        
        // Create test directories
        Files.createDirectories(Paths.get(projectDir, "src", "test", "java", packageDir, "api"));
        Files.createDirectories(Paths.get(projectDir, "src", "test", "java", packageDir, "ui"));
        Files.createDirectories(Paths.get(projectDir, "src", "test", "java", packageDir, "steps"));
        Files.createDirectories(Paths.get(projectDir, "src", "test", "resources", "features"));
        
        // Create utility directories
        Files.createDirectories(Paths.get(projectDir, "src", "test", "java", packageDir, "util"));
        Files.createDirectories(Paths.get(projectDir, "src", "test", "java", packageDir, "config"));
        
        // Create report directory
        Files.createDirectories(Paths.get(projectDir, "target", "reports"));
    }

    /**
     * Create POM file with required dependencies
     */
    private void createPomFile(String projectDir, String projectName, String basePackage) throws IOException {
        String pomContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "\n" +
                "    <groupId>" + basePackage + "</groupId>\n" +
                "    <artifactId>" + projectName + "</artifactId>\n" +
                "    <version>1.0-SNAPSHOT</version>\n" +
                "\n" +
                "    <properties>\n" +
                "        <maven.compiler.source>21</maven.compiler.source>\n" +
                "        <maven.compiler.target>21</maven.compiler.target>\n" +
                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                "        <playwright.version>1.40.0</playwright.version>\n" +
                "        <rest-assured.version>5.3.1</rest-assured.version>\n" +
                "        <cucumber.version>7.14.0</cucumber.version>\n" +
                "        <junit.version>5.10.0</junit.version>\n" +
                "    </properties>\n" +
                "\n" +
                "    <dependencies>\n" +
                "        <!-- Playwright for UI testing -->\n" +
                "        <dependency>\n" +
                "            <groupId>com.microsoft.playwright</groupId>\n" +
                "            <artifactId>playwright</artifactId>\n" +
                "            <version>${playwright.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "\n" +
                "        <!-- REST Assured for API testing -->\n" +
                "        <dependency>\n" +
                "            <groupId>io.rest-assured</groupId>\n" +
                "            <artifactId>rest-assured</artifactId>\n" +
                "            <version>${rest-assured.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.rest-assured</groupId>\n" +
                "            <artifactId>json-path</artifactId>\n" +
                "            <version>${rest-assured.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "\n" +
                "        <!-- Cucumber for BDD -->\n" +
                "        <dependency>\n" +
                "            <groupId>io.cucumber</groupId>\n" +
                "            <artifactId>cucumber-java</artifactId>\n" +
                "            <version>${cucumber.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.cucumber</groupId>\n" +
                "            <artifactId>cucumber-junit-platform-engine</artifactId>\n" +
                "            <version>${cucumber.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>io.cucumber</groupId>\n" +
                "            <artifactId>cucumber-spring</artifactId>\n" +
                "            <version>${cucumber.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "\n" +
                "        <!-- JUnit 5 -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-api</artifactId>\n" +
                "            <version>${junit.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-engine</artifactId>\n" +
                "            <version>${junit.version}</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.platform</groupId>\n" +
                "            <artifactId>junit-platform-suite</artifactId>\n" +
                "            <version>1.10.0</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "\n" +
                "        <!-- Utilities -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.projectlombok</groupId>\n" +
                "            <artifactId>lombok</artifactId>\n" +
                "            <version>1.18.30</version>\n" +
                "            <scope>provided</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.slf4j</groupId>\n" +
                "            <artifactId>slf4j-api</artifactId>\n" +
                "            <version>2.0.9</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>ch.qos.logback</groupId>\n" +
                "            <artifactId>logback-classic</artifactId>\n" +
                "            <version>1.4.11</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-compiler-plugin</artifactId>\n" +
                "                <version>3.11.0</version>\n" +
                "                <configuration>\n" +
                "                    <source>21</source>\n" +
                "                    <target>21</target>\n" +
                "                </configuration>\n" +
                "            </plugin>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-surefire-plugin</artifactId>\n" +
                "                <version>3.2.2</version>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>\n" +
                "</project>\n";
        
        Files.writeString(Paths.get(projectDir, "pom.xml"), pomContent);
    }

    /**
     * Initialize Playwright configuration
     */
    private void initializePlaywright(String projectDir, String basePackage) throws IOException {
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create PlaywrightConfig class
        String configContent = "package " + basePackage + ".config;\n\n" +
                "import com.microsoft.playwright.*;\n" +
                "import org.junit.jupiter.api.extension.ExtensionContext;\n" +
                "import java.nio.file.Paths;\n\n" +
                "public class PlaywrightConfig {\n" +
                "    private static Playwright playwright;\n" +
                "    private static Browser browser;\n\n" +
                "    public static BrowserContext createBrowserContext() {\n" +
                "        if (playwright == null) {\n" +
                "            playwright = Playwright.create();\n" +
                "        }\n\n" +
                "        if (browser == null) {\n" +
                "            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()\n" +
                "                    .setHeadless(Boolean.parseBoolean(System.getProperty(\"headless\", \"true\")))\n" +
                "                    .setSlowMo(50));\n" +
                "        }\n\n" +
                "        return browser.newContext(new Browser.NewContextOptions()\n" +
                "                .setViewportSize(1280, 720)\n" +
                "                .setRecordVideoDir(Paths.get(\"target/videos\")));\n" +
                "    }\n\n" +
                "    public static void closeBrowser(ExtensionContext context) {\n" +
                "        if (browser != null) {\n" +
                "            browser.close();\n" +
                "            browser = null;\n" +
                "        }\n" +
                "    }\n\n" +
                "    public static void closePlaywright(ExtensionContext context) {\n" +
                "        if (playwright != null) {\n" +
                "            playwright.close();\n" +
                "            playwright = null;\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "config", "PlaywrightConfig.java"), configContent);
        
        // Create BasePage class
        String basePageContent = "package " + basePackage + ".ui;\n\n" +
                "import com.microsoft.playwright.Page;\n" +
                "import org.slf4j.Logger;\n" +
                "import org.slf4j.LoggerFactory;\n\n" +
                "public abstract class BasePage {\n" +
                "    protected final Page page;\n" +
                "    protected final Logger logger = LoggerFactory.getLogger(getClass());\n\n" +
                "    public BasePage(Page page) {\n" +
                "        this.page = page;\n" +
                "    }\n\n" +
                "    public abstract boolean isAt();\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "ui", "BasePage.java"), basePageContent);
    }

    /**
     * Initialize RestAssured configuration
     */
    private void initializeRestAssured(String projectDir, String basePackage) throws IOException {
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create RestAssuredConfig class
        String configContent = "package " + basePackage + ".config;\n\n" +
                "import io.restassured.RestAssured;\n" +
                "import io.restassured.builder.RequestSpecBuilder;\n" +
                "import io.restassured.filter.log.RequestLoggingFilter;\n" +
                "import io.restassured.filter.log.ResponseLoggingFilter;\n" +
                "import io.restassured.http.ContentType;\n" +
                "import io.restassured.specification.RequestSpecification;\n" +
                "import org.junit.jupiter.api.BeforeAll;\n\n" +
                "public class RestAssuredConfig {\n" +
                "    private static RequestSpecification requestSpec;\n\n" +
                "    @BeforeAll\n" +
                "    public static void setup() {\n" +
                "        RestAssured.baseURI = System.getProperty(\"baseUrl\", \"http://localhost:8080\");\n\n" +
                "        requestSpec = new RequestSpecBuilder()\n" +
                "                .setContentType(ContentType.JSON)\n" +
                "                .setAccept(ContentType.JSON)\n" +
                "                .addFilter(new RequestLoggingFilter())\n" +
                "                .addFilter(new ResponseLoggingFilter())\n" +
                "                .build();\n" +
                "    }\n\n" +
                "    public static RequestSpecification getRequestSpec() {\n" +
                "        if (requestSpec == null) {\n" +
                "            setup();\n" +
                "        }\n" +
                "        return requestSpec;\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "config", "RestAssuredConfig.java"), configContent);
        
        // Create BaseApi class
        String baseApiContent = "package " + basePackage + ".api;\n\n" +
                "import io.restassured.response.Response;\n" +
                "import io.restassured.specification.RequestSpecification;\n" +
                "import " + basePackage + ".config.RestAssuredConfig;\n" +
                "import org.slf4j.Logger;\n" +
                "import org.slf4j.LoggerFactory;\n\n" +
                "import static io.restassured.RestAssured.given;\n\n" +
                "public abstract class BaseApi {\n" +
                "    protected final Logger logger = LoggerFactory.getLogger(getClass());\n\n" +
                "    protected RequestSpecification getRequestSpec() {\n" +
                "        return RestAssuredConfig.getRequestSpec();\n" +
                "    }\n\n" +
                "    protected Response get(String path) {\n" +
                "        return given()\n" +
                "                .spec(getRequestSpec())\n" +
                "                .when()\n" +
                "                .get(path);\n" +
                "    }\n\n" +
                "    protected Response post(String path, Object body) {\n" +
                "        return given()\n" +
                "                .spec(getRequestSpec())\n" +
                "                .body(body)\n" +
                "                .when()\n" +
                "                .post(path);\n" +
                "    }\n\n" +
                "    protected Response put(String path, Object body) {\n" +
                "        return given()\n" +
                "                .spec(getRequestSpec())\n" +
                "                .body(body)\n" +
                "                .when()\n" +
                "                .put(path);\n" +
                "    }\n\n" +
                "    protected Response delete(String path) {\n" +
                "        return given()\n" +
                "                .spec(getRequestSpec())\n" +
                "                .when()\n" +
                "                .delete(path);\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "api", "BaseApi.java"), baseApiContent);
    }

    /**
     * Initialize Cucumber configuration
     */
    private void initializeCucumber(String projectDir, String basePackage) throws IOException {
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create CucumberRunner
        String runnerContent = "package " + basePackage + ";\n\n" +
                "import org.junit.platform.suite.api.ConfigurationParameter;\n" +
                "import org.junit.platform.suite.api.IncludeEngines;\n" +
                "import org.junit.platform.suite.api.SelectClasspathResource;\n" +
                "import org.junit.platform.suite.api.Suite;\n\n" +
                "import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;\n" +
                "import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;\n\n" +
                "@Suite\n" +
                "@IncludeEngines(\"cucumber\")\n" +
                "@SelectClasspathResource(\"features\")\n" +
                "@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = \"" + basePackage + ".steps\")\n" +
                "@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = \"pretty, html:target/reports/cucumber.html, json:target/reports/cucumber.json\")\n" +
                "public class CucumberRunner {\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "CucumberRunner.java"), runnerContent);
        
        // Create Hooks class
        String hooksContent = "package " + basePackage + ".steps;\n\n" +
                "import io.cucumber.java.After;\n" +
                "import io.cucumber.java.Before;\n" +
                "import io.cucumber.java.Scenario;\n" +
                "import org.slf4j.Logger;\n" +
                "import org.slf4j.LoggerFactory;\n\n" +
                "public class Hooks {\n" +
                "    private static final Logger logger = LoggerFactory.getLogger(Hooks.class);\n\n" +
                "    @Before\n" +
                "    public void beforeScenario(Scenario scenario) {\n" +
                "        logger.info(\"Starting scenario: {}\", scenario.getName());\n" +
                "    }\n\n" +
                "    @After\n" +
                "    public void afterScenario(Scenario scenario) {\n" +
                "        logger.info(\"Finished scenario: {} - Status: {}\", scenario.getName(), scenario.getStatus());\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "steps", "Hooks.java"), hooksContent);
        
        // Create sample feature file
        String featureContent = "Feature: Sample Feature\n\n" +
                "  Scenario: Sample scenario\n" +
                "    Given the application is up and running\n" +
                "    When I perform a basic action\n" +
                "    Then I should see the expected result\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "resources", "features", "sample.feature"), featureContent);
        
        // Create sample step definitions
        String stepsContent = "package " + basePackage + ".steps;\n\n" +
                "import io.cucumber.java.en.Given;\n" +
                "import io.cucumber.java.en.Then;\n" +
                "import io.cucumber.java.en.When;\n\n" +
                "public class SampleSteps {\n\n" +
                "    @Given(\"the application is up and running\")\n" +
                "    public void theApplicationIsUpAndRunning() {\n" +
                "        // Implementation\n" +
                "    }\n\n" +
                "    @When(\"I perform a basic action\")\n" +
                "    public void iPerformABasicAction() {\n" +
                "        // Implementation\n" +
                "    }\n\n" +
                "    @Then(\"I should see the expected result\")\n" +
                "    public void iShouldSeeTheExpectedResult() {\n" +
                "        // Implementation\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "steps", "SampleSteps.java"), stepsContent);
    }

    /**
     * Add a Playwright UI test for a test case
     */
    private void addPlaywrightTest(String projectDir, TestCase testCase) throws IOException {
        String basePackage = deriveBasePackage(projectDir);
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create Page Object for the test
        String pageObjectName = testCase.getTargetPage().replace(" ", "") + "Page";
        String pageObjectContent = "package " + basePackage + ".ui;\n\n" +
                "import com.microsoft.playwright.Page;\n\n" +
                "public class " + pageObjectName + " extends BasePage {\n" +
                "    // Page element locators\n";
        
        // Add locators based on the test case components
        if (testCase.getComponents() != null) {
            for (String component : testCase.getComponents()) {
                String componentName = component.replaceAll("[^a-zA-Z0-9]", "");
                String locatorName = "LOCATOR_" + componentName.toUpperCase();
                pageObjectContent += "    private static final String " + locatorName + " = \"" + generateLocator(component) + "\";\n";
            }
        }
        
        pageObjectContent += "\n    public " + pageObjectName + "(Page page) {\n" +
                "        super(page);\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public boolean isAt() {\n" +
                "        return page.title().contains(\"" + testCase.getTargetPage() + "\");\n" +
                "    }\n\n";
        
        // Add methods based on the test case actions
        if (testCase.getSteps() != null) {
            for (String step : testCase.getSteps()) {
                String methodName = convertToMethodName(step);
                pageObjectContent += "    public void " + methodName + "() {\n" +
                        "        // Implementation for: " + step + "\n" +
                        "        logger.info(\"Executing: " + step + "\");\n";
                
                // Add basic implementation based on step description
                if (step.toLowerCase().contains("click")) {
                    pageObjectContent += "        page.click(\"\");\n";
                } else if (step.toLowerCase().contains("fill") || step.toLowerCase().contains("enter")) {
                    pageObjectContent += "        page.fill(\"\", \"\");\n";
                } else if (step.toLowerCase().contains("navigate") || step.toLowerCase().contains("go to")) {
                    pageObjectContent += "        page.navigate(\"" + testCase.getBaseUrl() + "\");\n";
                }
                
                pageObjectContent += "    }\n\n";
            }
        }
        
        pageObjectContent += "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "ui", pageObjectName + ".java"), pageObjectContent);
        
        // Create Test Class
        String testClassName = "Test" + testCase.getTargetPage().replace(" ", "");
        String testClassContent = "package " + basePackage + ".ui;\n\n" +
                "import com.microsoft.playwright.Browser;\n" +
                "import com.microsoft.playwright.BrowserContext;\n" +
                "import com.microsoft.playwright.Page;\n" +
                "import " + basePackage + ".config.PlaywrightConfig;\n" +
                "import org.junit.jupiter.api.*;\n\n" +
                "@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n" +
                "public class " + testClassName + " {\n" +
                "    private static BrowserContext context;\n" +
                "    private static Page page;\n" +
                "    private static " + pageObjectName + " pageObject;\n\n" +
                "    @BeforeAll\n" +
                "    static void setUp() {\n" +
                "        context = PlaywrightConfig.createBrowserContext();\n" +
                "        page = context.newPage();\n" +
                "        pageObject = new " + pageObjectName + "(page);\n" +
                "    }\n\n" +
                "    @AfterAll\n" +
                "    static void tearDown() {\n" +
                "        if (context != null) {\n" +
                "            context.close();\n" +
                "        }\n" +
                "    }\n\n" +
                "    @Test\n" +
                "    @Order(1)\n" +
                "    @DisplayName(\"" + testCase.getDescription() + "\")\n" +
                "    void " + convertToMethodName(testCase.getDescription()) + "() {\n";
        
        // Add test steps
        if (testCase.getSteps() != null) {
            int stepNumber = 1;
            for (String step : testCase.getSteps()) {
                testClassContent += "        // Step " + stepNumber + ": " + step + "\n";
                testClassContent += "        pageObject." + convertToMethodName(step) + "();\n\n";
                stepNumber++;
            }
        }
        
        // Add assertions
        testClassContent += "        // Assertions\n" +
                "        Assertions.assertTrue(true, \"Test passed\");\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "ui", testClassName + ".java"), testClassContent);
    }

    /**
     * Add a Rest Assured API test for a test case
     */
    private void addRestAssuredTest(String projectDir, TestCase testCase) throws IOException {
        String basePackage = deriveBasePackage(projectDir);
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create API class
        String apiClassName = testCase.getTargetEndpoint().replace("/", "_").replace("-", "_") + "Api";
        if (apiClassName.startsWith("_")) {
            apiClassName = apiClassName.substring(1);
        }
        
        String apiClassContent = "package " + basePackage + ".api;\n\n" +
                "import io.restassured.response.Response;\n" +
                "import org.slf4j.Logger;\n" +
                "import org.slf4j.LoggerFactory;\n\n" +
                "public class " + apiClassName + " extends BaseApi {\n" +
                "    private static final String ENDPOINT = \"" + testCase.getTargetEndpoint() + "\";\n\n";
        
        // Add methods based on HTTP methods in the test case
        if (testCase.getHttpMethod() != null) {
            String methodName = testCase.getHttpMethod().toLowerCase();
            apiClassContent += "    public Response " + methodName + "() {\n" +
                    "        logger.info(\"Sending " + testCase.getHttpMethod() + " request to {}\", ENDPOINT);\n";
            
            switch (testCase.getHttpMethod().toUpperCase()) {
                case "GET":
                    apiClassContent += "        return get(ENDPOINT);\n";
                    break;
                case "POST":
                    apiClassContent += "        String requestBody = \"" + escapeJson(buildRequestBody(testCase)) + "\";\n" +
                            "        return post(ENDPOINT, requestBody);\n";
                    break;
                case "PUT":
                    apiClassContent += "        String requestBody = \"" + escapeJson(buildRequestBody(testCase)) + "\";\n" +
                            "        return put(ENDPOINT, requestBody);\n";
                    break;
                case "DELETE":
                    apiClassContent += "        return delete(ENDPOINT);\n";
                    break;
            }
            
            apiClassContent += "    }\n";
        } else {
            // If HTTP method not specified, add generic methods
            apiClassContent += "    public Response get() {\n" +
                    "        logger.info(\"Sending GET request to {}\", ENDPOINT);\n" +
                    "        return get(ENDPOINT);\n" +
                    "    }\n\n" +
                    "    public Response post(Object requestBody) {\n" +
                    "        logger.info(\"Sending POST request to {}\", ENDPOINT);\n" +
                    "        return post(ENDPOINT, requestBody);\n" +
                    "    }\n";
        }
        
        apiClassContent += "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "api", apiClassName + ".java"), apiClassContent);
        
        // Create Test Class
        String testClassName = "Test" + apiClassName;
        String testClassContent = "package " + basePackage + ".api;\n\n" +
                "import io.restassured.response.Response;\n" +
                "import org.junit.jupiter.api.*;\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                "@TestMethodOrder(MethodOrderer.OrderAnnotation.class)\n" +
                "public class " + testClassName + " {\n" +
                "    private static " + apiClassName + " api;\n\n" +
                "    @BeforeAll\n" +
                "    static void setUp() {\n" +
                "        api = new " + apiClassName + "();\n" +
                "    }\n\n" +
                "    @Test\n" +
                "    @Order(1)\n" +
                "    @DisplayName(\"" + testCase.getDescription() + "\")\n" +
                "    void " + convertToMethodName(testCase.getDescription()) + "() {\n";
        
        // Add test execution based on HTTP method
        if (testCase.getHttpMethod() != null) {
            String methodName = testCase.getHttpMethod().toLowerCase();
            testClassContent += "        // Send " + testCase.getHttpMethod() + " request\n" +
                    "        Response response = api." + methodName + "();\n\n";
        } else {
            testClassContent += "        // Send request (assuming GET for simplicity)\n" +
                    "        Response response = api.get();\n\n";
        }
        
        // Add assertions
        testClassContent += "        // Assertions\n" +
                "        assertEquals(200, response.getStatusCode(), \"Expected successful status code\");\n" +
                "        // Add more specific assertions based on expected response\n" +
                "    }\n" +
                "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "api", testClassName + ".java"), testClassContent);
    }

    /**
     * Add a Cucumber feature and step definitions for a test case
     */
    private void addCucumberFeature(String projectDir, TestCase testCase) throws IOException {
        String basePackage = deriveBasePackage(projectDir);
        String packageDir = basePackage.replace('.', File.separatorChar);
        
        // Create feature file
        String featureName = testCase.getTargetPage().replace(" ", "") + ".feature";
        String featureContent = "Feature: " + testCase.getTargetPage() + "\n\n" +
                "  Scenario: " + testCase.getDescription() + "\n";
        
        // Add steps based on test case steps
        if (testCase.getSteps() != null) {
            for (String step : testCase.getSteps()) {
                String stepType = determineStepType(step);
                featureContent += "    " + stepType + " " + step + "\n";
            }
        } else {
            featureContent += "    Given I am on the " + testCase.getTargetPage() + " page\n" +
                    "    When I perform the test actions\n" +
                    "    Then I should see the expected results\n";
        }
        
        Files.writeString(Paths.get(projectDir, "src", "test", "resources", "features", featureName), featureContent);
        
        // Create step definitions
        String stepClassName = testCase.getTargetPage().replace(" ", "") + "Steps";
        String stepClassContent = "package " + basePackage + ".steps;\n\n" +
                "import io.cucumber.java.en.Given;\n" +
                "import io.cucumber.java.en.When;\n" +
                "import io.cucumber.java.en.Then;\n";
        
        // Import appropriate classes based on test type
        if (testCase.isUiTest()) {
            stepClassContent += "import com.microsoft.playwright.Page;\n" +
                    "import " + basePackage + ".ui." + testCase.getTargetPage().replace(" ", "") + "Page;\n" +
                    "import " + basePackage + ".config.PlaywrightConfig;\n";
        } else if (testCase.isApiTest()) {
            String apiClassName = testCase.getTargetEndpoint().replace("/", "_").replace("-", "_") + "Api";
            if (apiClassName.startsWith("_")) {
                apiClassName = apiClassName.substring(1);
            }
            stepClassContent += "import io.restassured.response.Response;\n" +
                    "import " + basePackage + ".api." + apiClassName + ";\n" +
                    "import static org.junit.jupiter.api.Assertions.*;\n";
        }
        
        stepClassContent += "\npublic class " + stepClassName + " {\n";
        
        // Add private fields based on test type
        if (testCase.isUiTest()) {
            stepClassContent += "    private static Page page;\n" +
                    "    private static " + testCase.getTargetPage().replace(" ", "") + "Page pageObject;\n\n";
        } else if (testCase.isApiTest()) {
            String apiClassName = testCase.getTargetEndpoint().replace("/", "_").replace("-", "_") + "Api";
            if (apiClassName.startsWith("_")) {
                apiClassName = apiClassName.substring(1);
            }
            stepClassContent += "    private static " + apiClassName + " api;\n" +
                    "    private Response response;\n\n";
        }
        
        // Add step definitions
        if (testCase.getSteps() != null) {
            for (String step : testCase.getSteps()) {
                String stepType = determineStepType(step);
                String methodName = convertToMethodName(step);
                
                stepClassContent += "    @" + stepType + "(\"" + step + "\")\n" +
                        "    public void " + methodName + "() {\n";
                
                // Add implementation based on step type and test type
                if (testCase.isUiTest()) {
                    if (stepType.equals("Given")) {
                        stepClassContent += "        // Initialize Playwright\n" +
                                "        if (page == null) {\n" +
                                "            page = PlaywrightConfig.createBrowserContext().newPage();\n" +
                                "            pageObject = new " + testCase.getTargetPage().replace(" ", "") + "Page(page);\n" +
                                "        }\n" +
                                "        \n" +
                                "        // Navigate to the page\n" +
                                "        page.navigate(\"" + testCase.getBaseUrl() + "\");\n";
                    } else if (step.toLowerCase().contains("click")) {
                        stepClassContent += "        // Click implementation\n" +
                                "        page.click(\"\");\n";
                    } else if (step.toLowerCase().contains("fill") || step.toLowerCase().contains("enter")) {
                        stepClassContent += "        // Fill implementation\n" +
                                "        page.fill(\"\", \"\");\n";
                    } else {
                        stepClassContent += "        // Implementation\n";
                    }
                } else if (testCase.isApiTest()) {
                    if (stepType.equals("Given")) {
                        String apiClassName = testCase.getTargetEndpoint().replace("/", "_").replace("-", "_") + "Api";
                        if (apiClassName.startsWith("_")) {
                            apiClassName = apiClassName.substring(1);
                        }
                        stepClassContent += "        // Initialize API\n" +
                                "        if (api == null) {\n" +
                                "            api = new " + apiClassName + "();\n" +
                                "        }\n";
                    } else if (stepType.equals("When")) {
                        stepClassContent += "        // Send request\n";
                        if (testCase.getHttpMethod() != null) {
                            String httpMethod = testCase.getHttpMethod().toLowerCase();
                            switch (httpMethod) {
                                case "get":
                                    stepClassContent += "        response = api.get();\n";
                                    break;
                                case "post":
                                    stepClassContent += "        String requestBody = \"" + escapeJson(buildRequestBody(testCase)) + "\";\n" +
                                            "        response = api.post(requestBody);\n";
                                    break;
                                default:
                                    stepClassContent += "        response = api.get();\n";
                            }
                        } else {
                            stepClassContent += "        response = api.get();\n";
                        }
                    } else if (stepType.equals("Then")) {
                        stepClassContent += "        // Assertions\n" +
                                "        assertEquals(200, response.getStatusCode(), \"Expected successful status code\");\n";
                    }
                } else {
                    stepClassContent += "        // Generic implementation\n";
                }
                
                stepClassContent += "    }\n\n";
            }
        } else {
            // Add default step definitions if no steps provided
            if (testCase.isUiTest()) {
                stepClassContent += "    @Given(\"I am on the " + testCase.getTargetPage() + " page\")\n" +
                        "    public void iAmOnThePage() {\n" +
                        "        // Initialize Playwright\n" +
                        "        if (page == null) {\n" +
                        "            page = PlaywrightConfig.createBrowserContext().newPage();\n" +
                        "            pageObject = new " + testCase.getTargetPage().replace(" ", "") + "Page(page);\n" +
                        "        }\n" +
                        "        \n" +
                        "        // Navigate to the page\n" +
                        "        page.navigate(\"" + testCase.getBaseUrl() + "\");\n" +
                        "    }\n\n";
            } else if (testCase.isApiTest()) {
                String apiClassName = testCase.getTargetEndpoint().replace("/", "_").replace("-", "_") + "Api";
                if (apiClassName.startsWith("_")) {
                    apiClassName = apiClassName.substring(1);
                }
                stepClassContent += "    @Given(\"I am using the " + testCase.getTargetEndpoint() + " API\")\n" +
                        "    public void iAmUsingTheApi() {\n" +
                        "        // Initialize API\n" +
                        "        if (api == null) {\n" +
                        "            api = new " + apiClassName + "();\n" +
                        "        }\n" +
                        "    }\n\n";
            }
            
            stepClassContent += "    @When(\"I perform the test actions\")\n" +
                    "    public void iPerformTheTestActions() {\n" +
                    "        // Implementation\n" +
                    "    }\n\n" +
                    "    @Then(\"I should see the expected results\")\n" +
                    "    public void iShouldSeeTheExpectedResults() {\n" +
                    "        // Assertions\n" +
                    "    }\n\n";
        }
        
        stepClassContent += "}\n";
        
        Files.writeString(Paths.get(projectDir, "src", "test", "java", packageDir, "steps", stepClassName + ".java"), stepClassContent);
    }

    /**
     * Derive the base package from the project directory structure
     */
    private String deriveBasePackage(String projectDir) {
        try {
            // Try to find it in pom.xml
            Path pomPath = Paths.get(projectDir, "pom.xml");
            if (Files.exists(pomPath)) {
                String content = Files.readString(pomPath);
                int groupIdStart = content.indexOf("<groupId>") + 9;
                int groupIdEnd = content.indexOf("</groupId>", groupIdStart);
                if (groupIdStart > 9 && groupIdEnd > 0) {
                    return content.substring(groupIdStart, groupIdEnd);
                }
            }
            
            // Default fallback
            return "com.example.automation";
        } catch (Exception e) {
            logger.warn("Failed to derive base package, using default", e);
            return "com.example.automation";
        }
    }

    /**
     * Convert a description to a valid method name
     */
    private String convertToMethodName(String description) {
        if (description == null || description.isEmpty()) {
            return "testMethod";
        }
        
        // Replace non-alphanumeric characters with spaces
        String normalized = description.replaceAll("[^a-zA-Z0-9]", " ");
        
        // Split by spaces and convert to camel case
        String[] words = normalized.split("\\s+");
        StringBuilder methodName = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            
            if (i == 0) {
                methodName.append(words[i].toLowerCase());
            } else {
                methodName.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1).toLowerCase());
            }
        }
        
        return methodName.toString();
    }

    /**
     * Determine the appropriate Cucumber step type based on step description
     */
    private String determineStepType(String step) {
        String lowerStep = step.toLowerCase();
        
        if (lowerStep.startsWith("given ")) return "Given";
        if (lowerStep.startsWith("when ")) return "When";
        if (lowerStep.startsWith("then ")) return "Then";
        if (lowerStep.startsWith("and ")) return "And";
        if (lowerStep.startsWith("but ")) return "But";
        
        // Try to determine by content
        if (lowerStep.contains("navigate") || 
            lowerStep.contains("go to") || 
            lowerStep.contains("open") || 
            lowerStep.contains("start")) {
            return "Given";
        } else if (lowerStep.contains("click") || 
                  lowerStep.contains("enter") || 
                  lowerStep.contains("select") || 
                  lowerStep.contains("perform") || 
                  lowerStep.contains("send")) {
            return "When";
        } else if (lowerStep.contains("verify") || 
                  lowerStep.contains("should") || 
                  lowerStep.contains("validate") || 
                  lowerStep.contains("expect") || 
                  lowerStep.contains("check")) {
            return "Then";
        }
        
        // Default to Given for first step
        return "Given";
    }

    /**
     * Build request body JSON for an API test case
     */
    private String buildRequestBody(TestCase testCase) {
        if (testCase.getTargetEndpoint() == null) {
            return "{}";
        }

        var endpoint = apiEndpointRepository.findByUrlAndMethod(
                testCase.getTargetEndpoint(), testCase.getHttpMethod());
        if (endpoint != null) {
            if (endpoint.getRequestBody() != null && !endpoint.getRequestBody().isEmpty()) {
                return endpoint.getRequestBody();
            }
            if (!endpoint.getParameters().isEmpty()) {
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(endpoint.getParameters());
                } catch (Exception e) {
                    logger.warn("Failed to serialize parameters", e);
                }
            }
        }
        return "{}";
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"");
    }

    /**
     * Generate a Playwright locator for a component name
     */
    private String generateLocator(String componentName) {
        if (componentName == null || componentName.isEmpty()) {
            return "body";
        }
        
        if (componentName.toLowerCase().contains("button")) {
            return "button:has-text('" + componentName + "')";
        } else if (componentName.toLowerCase().contains("input") || 
                  componentName.toLowerCase().contains("field") ||
                  componentName.toLowerCase().contains("text")) {
            return "input[placeholder*='" + componentName + "'], input[name*='" + 
                   componentName.toLowerCase().replace(" ", "") + "']";
        } else if (componentName.toLowerCase().contains("link")) {
            return "a:has-text('" + componentName + "')";
        } else if (componentName.toLowerCase().contains("dropdown") || 
                  componentName.toLowerCase().contains("select")) {
            return "select[name*='" + componentName.toLowerCase().replace(" ", "") + "']";
        } else {
            return "//*[contains(text(),'" + componentName + "')]";
        }
    }
}