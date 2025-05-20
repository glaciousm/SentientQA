package com.projectoracle.service;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A test class that uses JUnit 5's @Order annotation to ensure tests run in sequence.
 * This helps identify exactly which test is causing problems.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SequentialTest {
    
    @Test
    @Order(1)
    void test1_basicAssertions() {
        assertTrue(true, "This test should always pass");
        assertEquals(2, 1 + 1, "Basic math should work");
    }
    
    @Test
    @Order(2)
    void test2_stringOperations() {
        String text = "Hello, world!";
        assertNotNull(text, "String should not be null");
        assertTrue(text.contains("world"), "String should contain 'world'");
    }
    
    @Test
    @Order(3)
    void test3_exceptionHandling() {
        try {
            // This should throw an exception
            Integer.parseInt("not a number");
            fail("Expected NumberFormatException was not thrown");
        } catch (NumberFormatException e) {
            // This is the expected behavior
            assertNotNull(e.getMessage(), "Exception message should not be null");
        }
    }
}