package com.projectoracle.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for cache management and statistics.
 * Provides endpoints for viewing cache statistics and clearing caches.
 */
@RestController
@RequestMapping("/api/v1/cache")
public class CacheController {

    @Autowired
    private CacheManager cacheManager;
    
    /**
     * Get statistics for all caches.
     * 
     * @return A map of cache names to statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        if (cacheManager instanceof CaffeineCacheManager) {
            // Get stats from Caffeine cache
            CaffeineCacheManager caffeineCacheManager = (CaffeineCacheManager) cacheManager;
            caffeineCacheManager.getCacheNames().forEach(cacheName -> {
                CaffeineCache caffeineCache = (CaffeineCache) caffeineCacheManager.getCache(cacheName);
                if (caffeineCache != null) {
                    com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                            caffeineCache.getNativeCache();
                    
                    Map<String, Object> cacheStats = new HashMap<>();
                    cacheStats.put("size", nativeCache.estimatedSize());
                    
                    if (nativeCache.stats() != null) {
                        cacheStats.put("hits", nativeCache.stats().hitCount());
                        cacheStats.put("misses", nativeCache.stats().missCount());
                        cacheStats.put("hitRate", nativeCache.stats().hitRate());
                        cacheStats.put("evictions", nativeCache.stats().evictionCount());
                        cacheStats.put("averageLoadPenalty", nativeCache.stats().averageLoadPenalty());
                    }
                    
                    stats.put(cacheName, cacheStats);
                }
            });
        } else {
            // For Redis or other cache managers
            cacheManager.getCacheNames().forEach(cacheName -> {
                Map<String, Object> cacheStats = new HashMap<>();
                cacheStats.put("type", cacheManager.getClass().getSimpleName());
                cacheStats.put("available", cacheManager.getCache(cacheName) != null);
                stats.put(cacheName, cacheStats);
            });
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Clear a specific cache.
     * 
     * @param cacheName The name of the cache to clear
     * @return Response indicating success or failure
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        Map<String, Object> response = new HashMap<>();
        
        if (cacheManager.getCacheNames().contains(cacheName)) {
            cacheManager.getCache(cacheName).clear();
            response.put("status", "success");
            response.put("message", "Cache '" + cacheName + "' cleared successfully");
        } else {
            response.put("status", "error");
            response.put("message", "Cache '" + cacheName + "' not found");
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clear all caches.
     * 
     * @return Response indicating success
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        Map<String, Object> response = new HashMap<>();
        
        cacheManager.getCacheNames().forEach(cacheName -> 
            cacheManager.getCache(cacheName).clear()
        );
        
        response.put("status", "success");
        response.put("message", "All caches cleared successfully");
        
        return ResponseEntity.ok(response);
    }
}