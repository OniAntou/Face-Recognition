package com.example.facedetection.detector;

import com.example.facedetection.service.FaceDetection;
import org.opencv.core.Mat;

import java.util.List;

/**
 * Common interface for all face detection engines.
 * Provides a unified API for YOLO, SSD, and Haar cascade detectors.
 */
public interface FaceDetector extends AutoCloseable {

    /**
     * Gets the detector name for display/logging.
     *
     * @return detector name (e.g., "YOLOv8", "SSD Caffe", "Haar")
     */
    String getName();

    /**
     * Detects faces in the given frame.
     *
     * @param frame the input image (BGR format)
     * @return list of detected faces with bounding boxes and confidence scores
     */
    List<FaceDetection> detect(Mat frame);

    /**
     * Checks if this detector is available (models loaded successfully).
     *
     * @return true if detector can be used
     */
    boolean isAvailable();

    /**
     * Gets the detector's confidence threshold.
     *
     * @return minimum confidence score (0.0 - 1.0)
     */
    float getConfidenceThreshold();

    /**
     * Releases detector resources.
     * Implementation should be idempotent (safe to call multiple times).
     */
    @Override
    void close();
}
