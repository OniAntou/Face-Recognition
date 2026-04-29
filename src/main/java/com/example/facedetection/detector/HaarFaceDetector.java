package com.example.facedetection.detector;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.service.FaceDetection;
import com.example.facedetection.util.MatUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Haar cascade face detector implementing FaceDetector interface.
 * Fallback detector with no external dependencies.
 */
public class HaarFaceDetector implements FaceDetector {

    private static final Logger logger = LoggerFactory.getLogger(HaarFaceDetector.class);
    private static final String ENGINE_NAME = "Haar";

    private final CascadeClassifier classifier;
    private final float confidenceThreshold;
    private final double scaleFactor;
    private final int minNeighbors;
    private final Size minFaceSize;

    /**
     * Creates a Haar cascade face detector with default parameters from config.
     *
     * @param cascadePath path to Haar cascade XML file
     */
    public HaarFaceDetector(String cascadePath) {
        this(cascadePath, AppConfig.getInstance());
    }

    /**
     * Creates a Haar cascade face detector with configuration.
     *
     * @param cascadePath path to Haar cascade XML file
     * @param config application configuration
     */
    public HaarFaceDetector(String cascadePath, AppConfig config) {
        this(cascadePath, config.haarDefaultConfidence, config.haarScaleFactor,
             config.haarMinNeighbors, config.haarMinFaceSize);
    }

    /**
     * Creates a Haar cascade face detector with explicit parameters.
     *
     * @param cascadePath path to Haar cascade XML file
     * @param confidenceThreshold minimum confidence (0.0 - 1.0)
     * @param scaleFactor scale factor for detection
     * @param minNeighbors minimum neighbors for detection
     * @param minFaceSize minimum face size for detection
     */
    public HaarFaceDetector(String cascadePath, float confidenceThreshold,
                            double scaleFactor, int minNeighbors, int minFaceSize) {
        this.confidenceThreshold = confidenceThreshold;
        this.scaleFactor = scaleFactor;
        this.minNeighbors = minNeighbors;
        this.minFaceSize = new Size(minFaceSize, minFaceSize);

        this.classifier = new CascadeClassifier(cascadePath);

        if (classifier.empty()) {
            logger.error("Failed to load Haar cascade from {}", cascadePath);
        } else {
            logger.info("{} detector initialized", ENGINE_NAME);
        }
    }

    @Override
    public String getName() {
        return ENGINE_NAME;
    }

    @Override
    public List<FaceDetection> detect(Mat frame) {
        if (!isAvailable() || frame == null || frame.empty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        MatOfRect detectionsMat = new MatOfRect();

        try {
            classifier.detectMultiScale(
                    frame, detectionsMat,
                    scaleFactor, minNeighbors, 0, minFaceSize
            );

            Rect[] faces = detectionsMat.toArray();
            List<FaceDetection> results = new ArrayList<>(faces.length);

            for (Rect face : faces) {
                // Haar doesn't give confidence, use default based on detector reliability
                float confidence = 0.75f;
                if (confidence >= confidenceThreshold) {
                    results.add(new FaceDetection(face, new Point[0], confidence, ENGINE_NAME));
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.debug("{} detected {} faces in {} ms",
                    ENGINE_NAME, results.size(), processingTime);

            return results;
        } finally {
            MatUtils.safeRelease(detectionsMat);
        }
    }

    @Override
    public boolean isAvailable() {
        return classifier != null && !classifier.empty();
    }

    @Override
    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    @Override
    public void close() {
        // CascadeClassifier doesn't have explicit close
        logger.info("{} detector closed", ENGINE_NAME);
    }

    /**
     * Gets the underlying CascadeClassifier for advanced use.
     *
     * @return the classifier instance
     */
    public CascadeClassifier getClassifier() {
        return classifier;
    }
}
