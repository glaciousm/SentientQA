package com.projectoracle.service.integration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of synchronizing with an external knowledge source.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {
    
    /**
     * Whether the sync was successful
     */
    private boolean success;
    
    /**
     * Total number of items synchronized
     */
    private int totalItems;
    
    /**
     * Message describing the sync result
     */
    private String message;
    
    /**
     * Number of new items found
     */
    private int newItems;
    
    /**
     * Number of updated items
     */
    private int updatedItems;
    
    /**
     * Number of deleted items
     */
    private int deletedItems;
    
    /**
     * Time taken for synchronization in milliseconds
     */
    private long elapsedTimeMs;
    
    /**
     * Simple constructor for error cases
     */
    public SyncResult(boolean success, int totalItems, String message) {
        this.success = success;
        this.totalItems = totalItems;
        this.message = message;
        this.newItems = 0;
        this.updatedItems = 0;
        this.deletedItems = 0;
        this.elapsedTimeMs = 0;
    }
}