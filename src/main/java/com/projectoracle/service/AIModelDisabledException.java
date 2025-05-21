package com.projectoracle.service;

/**
 * Exception thrown when AI model functionality is disabled
 */
public class AIModelDisabledException extends RuntimeException {
    
    public AIModelDisabledException(String message) {
        super(message);
    }
    
    public AIModelDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}