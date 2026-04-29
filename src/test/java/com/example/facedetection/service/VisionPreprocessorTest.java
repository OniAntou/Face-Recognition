package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionPreprocessorTest {

    private VisionPreprocessor preprocessor;

    @BeforeAll
    static void setup() {
        OpenCV.loadShared();
    }

    @BeforeEach
    void init() {
        preprocessor = new VisionPreprocessor(AppConfig.getInstance());
    }

    @Test
    void detectsOverexposedFrames() {
        Mat bright = new Mat(120, 120, CvType.CV_8UC3, new Scalar(255, 255, 255));
        try {
            assertTrue(preprocessor.isLikelyOverexposed(bright));
        } finally {
            bright.release();
        }
    }

    @Test
    void ignoresNormallyLitFrames() {
        Mat normal = new Mat(120, 120, CvType.CV_8UC3, new Scalar(120, 120, 120));
        try {
            assertFalse(preprocessor.isLikelyOverexposed(normal));
            assertEquals(120.0, preprocessor.estimateBrightness(normal), 1.0);
        } finally {
            normal.release();
        }
    }

    @Test
    void preservesImageDimensionsWhenEnhancing() {
        Mat bright = new Mat(90, 160, CvType.CV_8UC3, new Scalar(250, 250, 250));
        Mat enhanced = preprocessor.prepareForFaceDetection(bright);
        try {
            assertNotNull(enhanced);
            assertFalse(enhanced.empty());
            assertEquals(bright.rows(), enhanced.rows());
            assertEquals(bright.cols(), enhanced.cols());
            assertEquals(3, enhanced.channels());
        } finally {
            enhanced.release();
            bright.release();
        }
    }
}
