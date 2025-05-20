package com.projectoracle.service;

import com.projectoracle.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TestGenerationServiceTest {

    @Mock
    private AIModelService aiModelService;

    @Mock
    private CodeAnalysisService codeAnalysisService;

    @InjectMocks
    private TestGenerationService testGenerationService;

    private MethodInfo mockMethodInfo;

    @BeforeEach
    void setUp() {
        // Create a mock MethodInfo for testing
        List<ParameterInfo> parameters = new ArrayList<>();
        parameters.add(new ParameterInfo("int", "a"));
        parameters.add(new ParameterInfo("int", "b"));
        
        List<String> exceptions = new ArrayList<>();
        exceptions.add("IllegalArgumentException");
        
        mockMethodInfo = MethodInfo.builder()
                .packageName("com.example")
                .className("Calculator")
                .methodName("add")
                .returnType("int")
                .parameters(parameters)
                .exceptions(exceptions)
                .javadoc("/**\n * Adds two integers and returns the result.\n * @param a first integer\n * @param b second integer\n * @return sum of a and b\n * @throws IllegalArgumentException if either parameter is negative\n */")
                .isPublic(true)
                .isStatic(false)
                .body("if (a < 0 || b < 0) {\n    throw new IllegalArgumentException(\"Parameters must be non-negative\");\n}\nreturn a + b;")
                .build();
    }

    @Test
    void testGenerateTestForMethod() {
        // Given
        String mockGeneratedCode = "@Test\npublic void testAdd_validInputs_returnSum() {\n    // Given\n    Calculator calculator = new Calculator();\n    int a = 5;\n    int b = 3;\n    \n    // When\n    int result = calculator.add(a, b);\n    \n    // Then\n    assertEquals(8, result);\n}";
        when(aiModelService.generateText(anyString(), anyInt())).thenReturn(mockGeneratedCode);

        // When
        TestCase result = testGenerationService.generateTestForMethod(mockMethodInfo);

        // Then
        assertNotNull(result);
        assertEquals("Testadd", result.getName());
        assertEquals("Test for add(int a, int b)", result.getDescription());
        assertEquals(TestCase.TestType.UNIT, result.getType());
        assertEquals(TestCase.TestPriority.MEDIUM, result.getPriority());
        assertEquals(TestCase.TestStatus.GENERATED, result.getStatus());
        assertEquals("com.example", result.getPackageName());
        assertEquals("TestCalculator", result.getClassName());
        assertEquals("testadd", result.getMethodName());
        assertEquals(mockGeneratedCode, result.getSourceCode());
        assertEquals(0.8, result.getConfidenceScore());
    }
}