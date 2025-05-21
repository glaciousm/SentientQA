package com.projectoracle.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for tracking model state without creating circular dependencies.
 * This service is used by both AIModelService and ModelStartupService.
 */
@Service
public class ModelStateService {

    private static final Logger logger = LoggerFactory.getLogger(ModelStateService.class);

    private volatile boolean modelsReady = false;
    private volatile String initializationError = null;

    /**
     * Set the models ready state
     * 
     * @param ready whether models are ready for use
     */
    public void setModelsReady(boolean ready) {
        this.modelsReady = ready;
        logger.info("Models ready state set to: {}", ready);
    }

    /**
     * Set the initialization error message
     * 
     * @param error the error message
     */
    public void setInitializationError(String error) {
        this.initializationError = error;
        logger.error("Model initialization error: {}", error);
    }

    /**
     * Check if models are ready
     * 
     * @return true if models are ready for use
     */
    public boolean areModelsReady() {
        return modelsReady;
    }

    /**
     * Get the initialization error message
     * 
     * @return the error message or null if no error
     */
    public String getInitializationError() {
        return initializationError;
    }
}