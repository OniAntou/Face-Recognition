package com.example.facedetection.cache;

import com.example.facedetection.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of gender prediction results for tracked faces.
 * Reduces redundant gender classification by caching results per face ID.
 */
public class GenderCacheManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GenderCacheManager.class);

    private final Map<Integer, Deque<String[]>> cache;
    private final int voteWindow;

    public GenderCacheManager(AppConfig config) {
        this.cache = new ConcurrentHashMap<>();
        this.voteWindow = Math.max(1, config.genderVoteWindow);
    }

    /**
     * Stores gender prediction result for a face.
     *
     * @param faceId the tracked face ID
     * @param result gender result array [gender, confidence]
     */
    public void put(int faceId, String[] result) {
        if (result != null && result.length >= 2) {
            Deque<String[]> history = cache.computeIfAbsent(faceId, ignored -> new ArrayDeque<>());
            synchronized (history) {
                history.addLast(copy(result));
                while (history.size() > voteWindow) {
                    history.removeFirst();
                }
            }
            logger.debug("Cached gender observation for face {}: {} {}", faceId, result[0], result[1]);
        }
    }

    /**
     * Retrieves cached gender result for a face.
     *
     * @param faceId the tracked face ID
     * @return gender result array or null if not cached
     */
    public String[] get(int faceId) {
        Deque<String[]> history = cache.get(faceId);
        if (history == null) {
            return null;
        }

        synchronized (history) {
            if (history.isEmpty()) {
                return null;
            }

            Map<String, VoteStats> votes = new LinkedHashMap<>();
            String[] latest = null;
            for (String[] result : history) {
                latest = result;
                String label = result[0];
                if (label == null || label.isBlank() || "Unknown".equalsIgnoreCase(label)) {
                    continue;
                }

                VoteStats stats = votes.computeIfAbsent(label, ignored -> new VoteStats());
                stats.count++;
                stats.confidenceTotal += parseConfidence(result[1]);
            }

            if (votes.isEmpty()) {
                return copy(latest);
            }

            String winner = null;
            VoteStats winningStats = null;
            for (Map.Entry<String, VoteStats> entry : votes.entrySet()) {
                VoteStats candidate = entry.getValue();
                if (winningStats == null
                        || candidate.count > winningStats.count
                        || (candidate.count == winningStats.count
                        && candidate.averageConfidence() > winningStats.averageConfidence())) {
                    winner = entry.getKey();
                    winningStats = candidate;
                }
            }

            int confidence = (int) Math.round(winningStats.averageConfidence());
            return new String[]{winner, confidence + "%"};
        }
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

    private static String[] copy(String[] result) {
        return new String[]{result[0], result[1]};
    }

    private static int parseConfidence(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.replace("%", "").trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class VoteStats {
        private int count;
        private double confidenceTotal;

        private double averageConfidence() {
            return count == 0 ? 0.0 : confidenceTotal / count;
        }
    }

    @Override
    public void close() {
        clear();
        logger.info("GenderCacheManager closed");
    }
}
