package com.example.facedetection.detector;

import com.example.facedetection.service.FaceDetection;

import java.util.Collections;
import java.util.List;

/**
 * Result record for face detection operations.
 *
 * @param detections list of detected faces
 * @param engineName name of the detector that produced this result
 * @param confidence average confidence of detections
 * @param processingTimeMs time taken for detection in milliseconds
 */
public record DetectorResult(
        List<FaceDetection> detections,
        String engineName,
        float confidence,
        long processingTimeMs
) {
    /**
     * Creates an empty result.
     *
     * @param engineName name of the detector
     * @return empty result with zero confidence
     */
    public static DetectorResult empty(String engineName) {
        return new DetectorResult(Collections.emptyList(), engineName, 0.0f, 0L);
    }

    /**
     * Checks if any faces were detected.
     *
     * @return true if detections list is not empty
     */
    public boolean hasDetections() {
        return !detections.isEmpty();
    }

    /**
     * Gets the number of detected faces.
     *
     * @return detection count
     */
    public int count() {
        return detections.size();
    }
}
