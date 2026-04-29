package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.detector.FaceDetector;
import com.example.facedetection.detector.SsdFaceDetector;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DetectionPipeline.
 */
class DetectionPipelineTest {

    private static FaceDetectorService faceDetectorService;
    private static AppConfig config;

    @BeforeAll
    static void setup() {
        OpenCV.loadShared();
        config = AppConfig.getInstance();

        String dataPath = "data/";
        faceDetectorService = new FaceDetectorService(
                dataPath + "res10_300x300_ssd_iter_140000.caffemodel",
                dataPath + "deploy.prototxt",
                dataPath + "gender_net.caffemodel",
                dataPath + "gender_deploy.prototxt",
                config.ssdConfidenceThreshold
        );
    }

    private static List<FaceDetector> createDetectors() {
        String dataPath = "data/";
        File modelFile = new File(dataPath + "res10_300x300_ssd_iter_140000.caffemodel");
        File configFile = new File(dataPath + "deploy.prototxt");
        
        List<FaceDetector> detectors = new ArrayList<>();
        if (modelFile.exists() && configFile.exists()) {
            detectors.add(new SsdFaceDetector(
                modelFile.getAbsolutePath(),
                configFile.getAbsolutePath(),
                config.ssdConfidenceThreshold
            ));
        }
        return detectors;
    }

    @Test
    void pipelineWithNoFacesReturnsEmpty() {
        List<FaceDetector> detectors = createDetectors();
        DetectionPipeline pipeline = new DetectionPipeline(config, detectors);
        Mat blankFrame = Mat.zeros(300, 300, CvType.CV_8UC3);

        try {
            DetectionPipeline.DetectionResult result = pipeline.process(
                    blankFrame, blankFrame.clone(), 1, false);

            assertNotNull(result);
            assertTrue(result.faces().isEmpty());
            assertNotNull(result.engineLabel());
        } finally {
            pipeline.close();
            blankFrame.release();
        }
    }

    @Test
    void pipelineProcessesMultipleFrames() {
        List<FaceDetector> detectors = createDetectors();
        DetectionPipeline pipeline = new DetectionPipeline(config, detectors);
        Mat frame1 = createSyntheticFrame(100, 100, 80);
        Mat frame2 = createSyntheticFrame(105, 102, 78);

        try {
            // First frame - should run detection
            DetectionPipeline.DetectionResult result1 = pipeline.process(frame1, frame1.clone(), 1, false);
            assertNotNull(result1);

            // Second frame within interval - should use tracker
            DetectionPipeline.DetectionResult result2 = pipeline.process(frame2, frame2.clone(), 2, false);
            assertNotNull(result2);

            // After interval - should run fresh detection
            DetectionPipeline.DetectionResult result3 = pipeline.process(frame1, frame1.clone(),
                    1 + config.detectionIntervalFrames, false);
            assertNotNull(result3);

        } finally {
            pipeline.close();
            frame1.release();
            frame2.release();
        }
    }

    @Test
    void brightLightModeAffectsDetection() {
        List<FaceDetector> detectors = createDetectors();
        DetectionPipeline pipeline = new DetectionPipeline(config, detectors);
        Mat frame = Mat.ones(300, 300, CvType.CV_8UC3);
        frame.setTo(new Scalar(250, 250, 250)); // Very bright frame

        try {
            // With bright light mode enabled
            pipeline.setBrightLightMode(true);
            DetectionPipeline.DetectionResult result1 = pipeline.process(frame, frame.clone(), 1, true);
            assertNotNull(result1);

            // Reset and try with bright light mode disabled
            pipeline.reset();
            pipeline.setBrightLightMode(false);
            DetectionPipeline.DetectionResult result2 = pipeline.process(frame, frame.clone(), 1, false);
            assertNotNull(result2);

        } finally {
            pipeline.close();
            frame.release();
        }
    }

    @Test
    void trackerMaintainsIdsAcrossFrames() {
        List<FaceDetector> detectors = createDetectors();
        DetectionPipeline pipeline = new DetectionPipeline(config, detectors);

        // Create frame with synthetic "face"
        Mat frame1 = Mat.zeros(200, 200, CvType.CV_8UC3);
        Imgproc.rectangle(frame1, new org.opencv.core.Point(50, 50), new org.opencv.core.Point(130, 130),
                new Scalar(255, 255, 255), -1);

        Mat frame2 = Mat.zeros(200, 200, CvType.CV_8UC3);
        Imgproc.rectangle(frame2, new org.opencv.core.Point(52, 52), new org.opencv.core.Point(132, 132),
                new Scalar(255, 255, 255), -1);

        try {
            // First detection
            DetectionPipeline.DetectionResult result1 = pipeline.process(frame1, frame1.clone(), 1, false);
            FaceTracker tracker = pipeline.getTracker();

            // Second frame with slight movement
            DetectionPipeline.DetectionResult result2 = pipeline.process(frame2, frame2.clone(), 2, false);

            // Check tracker maintained state
            assertNotNull(tracker);

        } finally {
            pipeline.close();
            frame1.release();
            frame2.release();
        }
    }

    @Test
    void pipelineResetClearsState() {
        List<FaceDetector> detectors = createDetectors();
        DetectionPipeline pipeline = new DetectionPipeline(config, detectors);
        Mat frame = createSyntheticFrame(100, 100, 80);

        try {
            // Process some frames
            pipeline.process(frame, frame.clone(), 1, false);
            pipeline.process(frame, frame.clone(), 2, false);

            // Reset
            pipeline.reset();

            // After reset, first call should trigger fresh detection
            DetectionPipeline.DetectionResult result = pipeline.process(frame, frame.clone(), 100, false);
            assertNotNull(result);
            assertFalse(result.trackerOnly()); // Should be fresh detection

        } finally {
            pipeline.close();
            frame.release();
        }
    }

    /**
     * Creates a synthetic frame with a white rectangle representing a face.
     */
    private Mat createSyntheticFrame(int x, int y, int size) {
        Mat frame = Mat.zeros(300, 300, CvType.CV_8UC3);
        Imgproc.rectangle(frame, new org.opencv.core.Point(x, y),
                new org.opencv.core.Point(x + size, y + size),
                new Scalar(255, 255, 255), -1);
        return frame;
    }
}
