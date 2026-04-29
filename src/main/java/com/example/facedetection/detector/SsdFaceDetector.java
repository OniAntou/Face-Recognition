package com.example.facedetection.detector;

import com.example.facedetection.service.FaceDetection;
import com.example.facedetection.util.MatUtils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SSD ResNet-10 face detector implementing FaceDetector interface.
 * Uses Caffe model for face detection.
 */
public class SsdFaceDetector implements FaceDetector {

    private static final Logger logger = LoggerFactory.getLogger(SsdFaceDetector.class);
    private static final String ENGINE_NAME = "SSD Caffe";
    private static final Size INPUT_SIZE = new Size(300, 300);
    private static final Scalar FACE_MEAN = new Scalar(104.0, 177.0, 123.0);

    private final Net faceNet;
    private final float confidenceThreshold;
    private final boolean available;

    /**
     * Creates an SSD face detector.
     *
     * @param modelPath path to Caffe model file (.caffemodel)
     * @param configPath path to Caffe config file (.prototxt)
     * @param confidenceThreshold minimum confidence (0.0 - 1.0)
     */
    public SsdFaceDetector(String modelPath, String configPath, float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;

        this.faceNet = Dnn.readNetFromCaffe(configPath, modelPath);
        this.available = !faceNet.empty();

        if (available) {
            enableHardwareAcceleration();
            logger.info("{} detector initialized", ENGINE_NAME);
        } else {
            logger.error("Failed to load {} model from {}", ENGINE_NAME, modelPath);
        }
    }

    @Override
    public String getName() {
        return ENGINE_NAME;
    }

    @Override
    public List<FaceDetection> detect(Mat frame) {
        if (!available || frame == null || frame.empty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        int imgWidth = frame.cols();
        int imgHeight = frame.rows();

        Mat blob = null;
        Mat detections = null;

        try {
            blob = Dnn.blobFromImage(frame, 1.0, INPUT_SIZE, FACE_MEAN, false, false);

            synchronized (faceNet) {
                faceNet.setInput(blob);
                detections = faceNet.forward();
            }

            List<FaceDetection> results = extractDetections(detections, imgWidth, imgHeight);
            long processingTime = System.currentTimeMillis() - startTime;

            logger.debug("{} detected {} faces in {} ms",
                    ENGINE_NAME, results.size(), processingTime);

            return results;
        } finally {
            MatUtils.safeRelease(detections);
            MatUtils.safeRelease(blob);
        }
    }

    @Override
    public boolean isAvailable() {
        return available && faceNet != null;
    }

    @Override
    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    @Override
    public void close() {
        // OpenCV Net doesn't have explicit close, rely on garbage collection
        logger.info("{} detector closed", ENGINE_NAME);
    }

    /**
     * Gets the underlying OpenCV Net for advanced use cases.
     *
     * @return the OpenCV Net instance
     */
    public Net getNet() {
        return faceNet;
    }

    private void enableHardwareAcceleration() {
        faceNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        faceNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        logger.debug("{} using CPU backend", ENGINE_NAME);
    }

    private List<FaceDetection> extractDetections(Mat detections, int imgWidth, int imgHeight) {
        int numDetections = detections.size(2);
        List<FaceDetection> results = new ArrayList<>();

        for (int i = 0; i < numDetections; i++) {
            float confidence = (float) detections.get(new int[]{0, 0, i, 2})[0];
            if (confidence <= confidenceThreshold) {
                continue;
            }

            double left = detections.get(new int[]{0, 0, i, 3})[0];
            double top = detections.get(new int[]{0, 0, i, 4})[0];
            double right = detections.get(new int[]{0, 0, i, 5})[0];
            double bottom = detections.get(new int[]{0, 0, i, 6})[0];

            int x1 = Math.max(0, (int) (left * imgWidth));
            int y1 = Math.max(0, (int) (top * imgHeight));
            int x2 = Math.min(imgWidth - 1, (int) (right * imgWidth));
            int y2 = Math.min(imgHeight - 1, (int) (bottom * imgHeight));

            if (x2 > x1 && y2 > y1) {
                Rect rect = new Rect(x1, y1, x2 - x1, y2 - y1);
                results.add(new FaceDetection(rect, new Point[0], confidence, ENGINE_NAME));
            }
        }

        return results;
    }
}
