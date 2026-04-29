package com.example.facedetection.cache;

import com.example.facedetection.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of gender prediction results for tracked faces.
 * Reduces redundant gender classification by caching results per face ID.
 */
public class GenderCacheManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GenderCacheManager.class);

    private final Map<Integer, String[]> cache;
    private final AppConfig config;

    public GenderCacheManager(AppConfig config) {
        this.config = config;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Stores gender prediction result for a face.
     *
     * @param faceId the tracked face ID
     * @param result gender result array [gender, confidence]
     */
    public void put(int faceId, String[] result) {
        if (result != null && result.length >= 2) {
            cache.put(faceId, result);
            logger.debug("Cached gender for face {}: {} {}", faceId, result[0], result[1]);
        }
    }

    /**
     * Retrieves cached gender result for a face.
     *
     * @param faceId the tracked face ID
     * @return gender result array or null if not cached
     */
    public String[] get(int faceId) {
        return cache.get(faceId);
    }

    /**
     * Checks if a face has cached gender data.
     *
     * @param faceId the tracked face ID
     * @return true if cached
     */
    public boolean contains(int faceId) {
        return cache.containsKey(faceId);
    }

    /**
     * Retains only entries for the given face IDs, removing all others.
     *
     * @param activeIds set of active face IDs to retain
     */
    public void retainAll(Set<Integer> activeIds) {
        cache.keySet().retainAll(activeIds);
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        cache.clear();
        logger.debug("Gender cache cleared");
    }

    /**
     * Gets the number of cached entries.
     *
     * @return cache size
     */
    public int size() {
        return cache.size();
    }

    @Override
    public void close() {
        clear();
        logger.info("GenderCacheManager closed");
    }
}
