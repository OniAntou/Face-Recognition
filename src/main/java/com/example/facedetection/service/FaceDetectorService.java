package com.example.facedetection.service;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for detecting faces in images using OpenCV Deep Learning (DNN).
 * <p>
 * Optimized for real-time performance:
 * <ul>
 *   <li>Pre-allocated {@link Scalar} constants to avoid per-frame GC pressure</li>
 *   <li>Thread-safe via {@code synchronized} on each network forward pass</li>
 *   <li>Deterministic native memory release with try/finally blocks</li>
 * </ul>
 */
public class FaceDetectorService {

    private static final Logger logger = LoggerFactory.getLogger(FaceDetectorService.class);

    // ── Pre-allocated constants (avoid per-frame allocation) ─────────────────
    private static final Scalar COLOR_BOX    = new Scalar(0, 255, 0);
    private static final Scalar COLOR_LABEL  = new Scalar(0, 255, 255);
    private static final Scalar FACE_MEAN    = new Scalar(104.0, 177.0, 123.0);
    private static final Scalar GENDER_MEAN  = new Scalar(78.4263377603, 87.7689143744, 114.895847746);
    private static final Size   FACE_INPUT   = new Size(300, 300);
    private static final Size   GENDER_INPUT = new Size(227, 227);

    private static final String[] GENDER_LABELS = {"Male", "Female"};

    private final Net faceNet;
    private final Net genderNet;
    private final float confidenceThreshold;

    // ── Lock objects for thread-safe DNN inference ───────────────────────────
    private final Object faceLock   = new Object();
    private final Object genderLock = new Object();

    public FaceDetectorService(String faceModelPath, String faceConfigPath,
                               String genderModelPath, String genderConfigPath) {
        this(faceModelPath, faceConfigPath, genderModelPath, genderConfigPath, 0.35f);
    }

    public FaceDetectorService(String faceModelPath, String faceConfigPath,
                               String genderModelPath, String genderConfigPath,
                               float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;

        this.faceNet = Dnn.readNetFromCaffe(faceConfigPath, faceModelPath);
        if (faceNet.empty()) {
            throw new RuntimeException("Could not load Face DNN model from " + faceModelPath);
        }

        this.genderNet = Dnn.readNetFromCaffe(genderConfigPath, genderModelPath);
        if (genderNet.empty()) {
            throw new RuntimeException("Could not load Gender DNN model from " + genderModelPath);
        }
        
        // Enable hardware acceleration if available
        enableHardwareAcceleration();
        
        logger.info("Face & Gender DNN Detectors loaded successfully.");
    }

    /**
     * Attempts to enable hardware acceleration (OpenCL/CUDA/Vulkan).
     * Falls back to CPU if hardware is not compatible.
     */
    public void enableHardwareAcceleration() {
        try {
            // Default to OpenCL if available, which works on most GPUs
            faceNet.setPreferableBackend(Dnn.DNN_BACKEND_DEFAULT);
            faceNet.setPreferableTarget(Dnn.DNN_TARGET_OPENCL);
            
            genderNet.setPreferableBackend(Dnn.DNN_BACKEND_DEFAULT);
            genderNet.setPreferableTarget(Dnn.DNN_TARGET_OPENCL);
            
            logger.info("Hardware acceleration (OpenCL) requested.");
        } catch (Exception e) {
            logger.warn("Could not enable hardware acceleration, falling back to CPU: {}", e.getMessage());
            faceNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
            faceNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        }
    }

    /**
     * Detects faces, predicts gender, and draws rectangles and labels.
     *
     * @param image the input image (will be modified in-place)
     * @return number of faces detected
     */
    public int detectAndDrawFaces(Mat image) {
        Rect[] faces = detectFaces(image);
        for (Rect rect : faces) {
            // Draw face bounding box
            Imgproc.rectangle(image, rect.tl(), rect.br(), COLOR_BOX, 2);

            // Predict and draw gender
            drawGenderLabel(image, rect);
        }
        return faces.length;
    }

    /**
     * Predicts gender for a face region and draws the label on the image.
     */
    public void drawGenderLabel(Mat image, Rect rect) {
        // Predict gender with confidence
        String[] result = predictGender(image, rect);
        String label = result[0];
        String confidence = result[1];

        // Draw label with confidence (e.g. "Male 92%")
        String displayText = label + " " + confidence;
        Imgproc.putText(image, displayText, new Point(rect.x, rect.y - 10),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_LABEL, 2);
    }

    /**
     * Predicts the gender for a single detected face region.
     * <p>
     * Key preprocessing steps for accurate prediction:
     * <ol>
     *   <li>Proportional padding (40%) around the face for context</li>
     *   <li>Square crop to prevent aspect ratio distortion</li>
     *   <li>BGR→RGB channel swap (swapRB=true) as required by the model</li>
     * </ol>
     *
     * @return String array: [0] = label ("Male"/"Female"/"Unknown"), [1] = confidence ("92%")
     */
    public String[] predictGender(Mat image, Rect faceRect) {
        Mat face = null;
        Mat blob = null;
        Mat genderPreds = null;
        try {
            // ── Step 1: Proportional padding (40% of face size) ─────────────
            // The model was trained with context around the face (forehead, chin).
            // A fixed pixel padding fails for different face sizes.
            double padRatioW = 0.4;
            double padRatioH = 0.4;
            int padW = (int) (faceRect.width * padRatioW);
            int padH = (int) (faceRect.height * padRatioH);

            int x1 = Math.max(0, faceRect.x - padW);
            int y1 = Math.max(0, faceRect.y - padH);
            int x2 = Math.min(image.cols(), faceRect.x + faceRect.width + padW);
            int y2 = Math.min(image.rows(), faceRect.y + faceRect.height + padH);

            if (x2 <= x1 || y2 <= y1) {
                return new String[]{"Unknown", ""};
            }

            // ── Step 2: Make the crop SQUARE to avoid distortion ────────────
            // The model expects 227×227 — stretching a rectangle into a square
            // distorts facial features and degrades accuracy.
            int cropW = x2 - x1;
            int cropH = y2 - y1;
            int side = Math.max(cropW, cropH);

            // Center the square around the face
            int centerX = (x1 + x2) / 2;
            int centerY = (y1 + y2) / 2;
            x1 = Math.max(0, centerX - side / 2);
            y1 = Math.max(0, centerY - side / 2);
            x2 = Math.min(image.cols(), x1 + side);
            y2 = Math.min(image.rows(), y1 + side);

            // Re-adjust if we hit image boundaries
            if (x2 - x1 < side) x1 = Math.max(0, x2 - side);
            if (y2 - y1 < side) y1 = Math.max(0, y2 - side);

            Rect squareRect = new Rect(x1, y1, x2 - x1, y2 - y1);
            face = new Mat(image, squareRect);

            // ── Step 3: Create blob ──────────────────────────────────────────
            // swapRB=false: Caffe model trained on BGR, OpenCV reads BGR — no swap needed.
            // Mean values are in BGR order: (B=78.43, G=87.77, R=114.90)
            blob = Dnn.blobFromImage(face, 1.0, GENDER_INPUT, GENDER_MEAN, false, false);

            synchronized (genderLock) {
                genderNet.setInput(blob);
                genderPreds = genderNet.forward();
            }

            // Get probabilities
            double p0 = genderPreds.get(0, 0)[0];
            double p1 = genderPreds.get(0, 1)[0];
            logger.debug("Gender raw output: p0(Male)={}, p1(Female)={}",
                    String.format("%.4f", p0), String.format("%.4f", p1));
            int classId = p0 > p1 ? 0 : 1;
            double maxProb = Math.max(p0, p1);
            int confidencePct = (int) Math.round(maxProb * 100);

            // If confidence is too low, mark as Unknown
            if (maxProb < 0.55) {
                return new String[]{"Unknown", confidencePct + "%"};
            }

            return new String[]{GENDER_LABELS[classId], confidencePct + "%"};
        } catch (Exception e) {
            logger.error("Error predicting gender", e);
            return new String[]{"Unknown", ""};
        } finally {
            safeRelease(genderPreds);
            safeRelease(blob);
            safeRelease(face);
        }
    }

    /**
     * Detects faces using SSD ResNet-10 model.
     *
     * @param image source image
     * @return array of bounding rectangles for detected faces
     */
    public Rect[] detectFaces(Mat image) {
        int imgWidth  = image.cols();
        int imgHeight = image.rows();

        Mat blob = Dnn.blobFromImage(image, 1.0, FACE_INPUT, FACE_MEAN, false, false);
        Mat detections = null;

        try {
            synchronized (faceLock) {
                faceNet.setInput(blob);
                detections = faceNet.forward(); // Output is [1, 1, N, 7]
            }

            int numDetections = detections.size(2);
            List<Rect> faceList = new ArrayList<>(numDetections);

            for (int i = 0; i < numDetections; i++) {
                double confidence = detections.get(new int[]{0, 0, i, 2})[0];

                if (confidence > confidenceThreshold) {
                    double left   = detections.get(new int[]{0, 0, i, 3})[0];
                    double top    = detections.get(new int[]{0, 0, i, 4})[0];
                    double right  = detections.get(new int[]{0, 0, i, 5})[0];
                    double bottom = detections.get(new int[]{0, 0, i, 6})[0];

                    // Convert normalized coords to pixel coords and clamp
                    int x1 = Math.max(0, (int) (left * imgWidth));
                    int y1 = Math.max(0, (int) (top * imgHeight));
                    int x2 = Math.min(imgWidth  - 1, (int) (right * imgWidth));
                    int y2 = Math.min(imgHeight - 1, (int) (bottom * imgHeight));

                    if (x2 > x1 && y2 > y1) {
                        faceList.add(new Rect(x1, y1, x2 - x1, y2 - y1));
                    }
                }
            }

            logger.debug("Found {} potential faces", faceList.size());
            return faceList.toArray(new Rect[0]);

        } finally {
            safeRelease(detections);
            safeRelease(blob);
        }
    }

    /**
     * Safely releases an OpenCV Mat, ignoring null or already-released mats.
     */
    private static void safeRelease(Mat mat) {
        if (mat != null) {
            try {
                mat.release();
            } catch (Exception ignored) {
                // Mat was already released or invalid
            }
        }
    }
}
