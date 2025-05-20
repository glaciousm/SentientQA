package com.projectoracle.service.basic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A very simple test class to verify that JUnit is working correctly
 */
public class BasicTest {
    
    @Test
    void testBasicAssertion() {
        // Basic assertion that should always pass
        assertTrue(true);
        assertFalse(false);
        assertEquals(2, 1 + 1);
    }
    
    @Test
    void testStringOperations() {
        String text = "Hello, world!";
        assertNotNull(text);
        assertTrue(text.contains("world"));
        assertEquals(13, text.length());
    }
}