package com.example.facedetection.cache;

import com.example.facedetection.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class GenderCacheManagerTest {

    @Test
    void returnsTheMajorityPredictionAcrossRecentFrames() {
        GenderCacheManager cache = new GenderCacheManager(AppConfig.getInstance());
        try {
            cache.put(7, new String[]{"Male", "86%"});
            cache.put(7, new String[]{"Female", "72%"});
            cache.put(7, new String[]{"Female", "74%"});

            assertArrayEquals(new String[]{"Female", "73%"}, cache.get(7));
        } finally {
            cache.close();
        }
    }
}
