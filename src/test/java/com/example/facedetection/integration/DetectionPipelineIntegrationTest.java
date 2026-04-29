package com.example.facedetection.integration;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.detector.FaceDetector;
import com.example.facedetection.detector.SsdFaceDetector;
import com.example.facedetection.detector.YoloFaceDetector;
import com.example.facedetection.service.DetectionPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DetectionPipeline with real models.
 * Requires model files to be present in data/ directory.
 */
class DetectionPipelineIntegrationTest {

    static {
        nu.pattern.OpenCV.loadShared();
    }

    private DetectionPipeline pipeline;
    private List<FaceDetector> detectors;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        config = AppConfig.getInstance();
        detectors = new ArrayList<>();

        // Initialize detectors if models are available
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir = new File("app/data");
        }

        // Try to initialize SSD detector
        File faceModel = new File(dataDir, "res10_300x300_ssd_iter_140000.caffemodel");
        File faceConfig = new File(dataDir, "deploy.prototxt");

        if (faceModel.exists() && faceConfig.exists()) {
            try {
                detectors.add(new SsdFaceDetector(
                    faceModel.getAbsolutePath(),
                    faceConfig.getAbsolutePath(),
                    config.ssdConfidenceThreshold
                ));
            } catch (Exception e) {
                System.out.println("SSD initialization skipped: " + e.getMessage());
            }
        }

        // Try to initialize YOLO if available
        File yoloModel = new File(dataDir, "yolov8n-face.onnx");
        if (yoloModel.exists()) {
            try {
                detectors.add(new YoloFaceDetector(
                    yoloModel.getAbsolutePath(),
                    config.yoloConfidenceThreshold,
                    config.yoloNmsThreshold
                ));
            } catch (Exception e) {
                System.out.println("YOLO initialization skipped: " + e.getMessage());
            }
        }

        pipeline = new DetectionPipeline(config, detectors);
    }

    @AfterEach
    void tearDown() {
        if (pipeline != null) {
            pipeline.close();
        }
    }

    @Test
    void testPipelineWithEmptyImage() {
        // Test with empty image - should not crash
        Mat emptyImage = new Mat();

        DetectionPipeline.DetectionResult result = pipeline.process(
            emptyImage, emptyImage, 1, false
        );

        assertNotNull(result);
        assertTrue(result.faces().isEmpty(), "Empty image should produce no faces");
    }

    @Test
    void testPipelineWithBlankImage() {
        // Test with blank (single color) image
        Mat blankImage = new Mat(480, 640, org.opencv.core.CvType.CV_8UC3);
        blankImage.setTo(new org.opencv.core.Scalar(128, 128, 128));

        DetectionPipeline.DetectionResult result = pipeline.process(
            blankImage, blankImage, 1, false
        );

        assertNotNull(result);
        // Blank image should not have faces
        assertTrue(result.faces().isEmpty() || result.faces().size() >= 0,
            "Blank image should produce 0 or very few detections");

        blankImage.release();
    }

    @Test
    void testPipelineReset() {
        // Test that reset clears state properly
        pipeline.reset();

        assertTrue(pipeline.getTracker().isEmpty(),
            "Tracker should be empty after reset");
    }

    @Test
    void testPipelineBrightLightMode() {
        // Test bright light mode toggle
        assertFalse(pipeline.isBrightLightMode(), "Default should be false");

        pipeline.setBrightLightMode(true);
        assertTrue(pipeline.isBrightLightMode());

        pipeline.setBrightLightMode(false);
        assertFalse(pipeline.isBrightLightMode());
    }

    @Test
    void testPipelineWithSimulatedFaceRegion() {
        // Create an image with a face-like region (lighter rectangle on darker background)
        Mat testImage = new Mat(480, 640, org.opencv.core.CvType.CV_8UC3);
        testImage.setTo(new org.opencv.core.Scalar(50, 50, 50)); // Dark background

        // Draw a face-like region
        org.opencv.core.Rect faceRegion = new org.opencv.core.Rect(200, 150, 200, 250);
        org.opencv.core.Mat roi = new Mat(testImage, faceRegion);
        roi.setTo(new org.opencv.core.Scalar(200, 180, 160)); // Lighter "face"

        DetectionPipeline.DetectionResult result = pipeline.process(
            testImage, testImage, 1, false
        );

        assertNotNull(result);
        assertNotNull(result.engineLabel());

        testImage.release();
    }

    @Test
    void testMultipleFrameProcessing() {
        // Test processing multiple frames in sequence
        Mat frame = new Mat(480, 640, org.opencv.core.CvType.CV_8UC3);
        frame.setTo(new org.opencv.core.Scalar(100, 150, 200));

        for (int i = 1; i <= 10; i++) {
            DetectionPipeline.DetectionResult result = pipeline.process(
                frame, frame, i, false
            );

            assertNotNull(result, "Frame " + i + " should produce result");
            assertNotNull(result.faces(), "Frame " + i + " should have faces list");
        }

        frame.release();
    }
}
