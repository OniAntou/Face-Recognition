package com.example.facedetection.detector;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.config.ModelPaths;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoloFaceDetectorTest {

    @BeforeAll
    static void loadNativeLibraries() {
        OpenCV.loadLocally();
    }

    @Test
    void loadsTheBundledModelWithTheConfiguredRuntime() throws Exception {
        ModelPaths paths = ModelPaths.resolve();
        assertTrue(paths.hasYolo(), paths::describeMissingFiles);

        YoloFaceDetector detector = new YoloFaceDetector(
                paths.yoloModel().toString(),
                AppConfig.getInstance().yoloConfidenceThreshold,
                AppConfig.getInstance().yoloNmsThreshold);
        try {
            assertTrue(detector.isAvailable());
            Mat blank = Mat.zeros(640, 640, CvType.CV_8UC3);
            try {
                assertNotNull(detector.detect(blank));
            } finally {
                blank.release();
            }
        } finally {
            detector.close();
        }
    }

    @Test
    void rejectsMissingModelPathBeforeCreatingASession() {
        Exception error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new YoloFaceDetector("missing-model.onnx", 0.5f, 0.45f));
        assertFalse(error.getMessage().isBlank());
    }
}
