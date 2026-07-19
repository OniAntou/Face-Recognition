package com.example.facedetection.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class GenderClassifierTest {

    @Test
    void rejectsLowConfidencePredictionsInsteadOfShowingAClass() {
        assertArrayEquals(
                new String[]{"Unknown", "68%"},
                GenderClassifier.classify(0.68, 0.32, 0.70)
        );
    }
}
