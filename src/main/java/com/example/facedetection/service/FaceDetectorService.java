package com.example.facedetection.service;

import com.example.facedetection.util.MatUtils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for detecting faces and predicting gender using OpenCV DNN models.
 */
public class FaceDetectorService {

    private static final Logger logger = LoggerFactory.getLogger(FaceDetectorService.class);

    private static final Scalar COLOR_BOX = new Scalar(0, 255, 0);
    private static final Scalar COLOR_LABEL = new Scalar(0, 255, 255);
    private static final Scalar FACE_MEAN = new Scalar(104.0, 177.0, 123.0);
    private static final Scalar GENDER_MEAN = new Scalar(78.4263377603, 87.7689143744, 114.895847746);
    private static final Size FACE_INPUT = new Size(300, 300);
    private static final Size GENDER_INPUT = new Size(227, 227);
    private static final String[] GENDER_LABELS = {"Male", "Female"};

    private final Net faceNet;
    private final Net genderNet;
    private final float confidenceThreshold;
    private final Object faceLock = new Object();
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

        enableHardwareAcceleration();
        logger.info("Face & Gender DNN Detectors loaded successfully.");
    }

    public void enableHardwareAcceleration() {
        faceNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        faceNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);

        genderNet.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
        genderNet.setPreferableTarget(Dnn.DNN_TARGET_CPU);

        logger.info("Using CPU backend for maximum compatibility.");
    }

    public Net getFaceNet() {
        return faceNet;
    }

    public Net getGenderNet() {
        return genderNet;
    }

    public int detectAndDrawFaces(Mat image) {
        Rect[] faces = detectFaces(image);
        for (Rect rect : faces) {
            Imgproc.rectangle(image, rect.tl(), rect.br(), COLOR_BOX, 2);
            drawGenderLabel(image, rect);
        }
        return faces.length;
    }

    public void drawGenderLabel(Mat image, Rect rect) {
        String[] result = predictGender(image, rect);
        String displayText = result[0] + " " + result[1];
        Imgproc.putText(image, displayText, new Point(rect.x, rect.y - 10),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, COLOR_LABEL, 2);
    }

    public String[] predictGender(Mat image, Rect faceRect) {
        return predictGender(image, faceRect, null);
    }

    public String[] predictGender(Mat image, Rect faceRect, Point[] landmarks) {
        Mat face = null;
        Mat blob = null;
        Mat genderPreds = null;
        try {
            face = extractAlignedFace(image, landmarks);
            if (face == null || face.empty()) {
                MatUtils.safeRelease(face);
                face = extractDefaultGenderCrop(image, faceRect);
            }

            if (face == null || face.empty()) {
                return new String[]{"Unknown", ""};
            }

            blob = Dnn.blobFromImage(face, 1.0, GENDER_INPUT, GENDER_MEAN, false, false);

            synchronized (genderLock) {
                genderNet.setInput(blob);
                genderPreds = genderNet.forward();
            }

            double p0 = genderPreds.get(0, 0)[0];
            double p1 = genderPreds.get(0, 1)[0];
            logger.debug("Gender raw output: p0(Male)={}, p1(Female)={}",
                    String.format("%.4f", p0), String.format("%.4f", p1));

            int classId = p0 > p1 ? 0 : 1;
            double maxProb = Math.max(p0, p1);
            int confidencePct = (int) Math.round(maxProb * 100);
            if (maxProb < 0.55) {
                return new String[]{"Unknown", confidencePct + "%"};
            }

            return new String[]{GENDER_LABELS[classId], confidencePct + "%"};
        } catch (Exception e) {
            logger.error("Error predicting gender", e);
            return new String[]{"Unknown", ""};
        } finally {
            MatUtils.safeRelease(genderPreds);
            MatUtils.safeRelease(blob);
            MatUtils.safeRelease(face);
        }
    }

    private Mat extractDefaultGenderCrop(Mat image, Rect faceRect) {
        double padRatioW = 0.4;
        double padRatioH = 0.4;
        int padW = (int) (faceRect.width * padRatioW);
        int padH = (int) (faceRect.height * padRatioH);

        int x1 = Math.max(0, faceRect.x - padW);
        int y1 = Math.max(0, faceRect.y - padH);
        int x2 = Math.min(image.cols(), faceRect.x + faceRect.width + padW);
        int y2 = Math.min(image.rows(), faceRect.y + faceRect.height + padH);
        if (x2 <= x1 || y2 <= y1) {
            return null;
        }

        int cropW = x2 - x1;
        int cropH = y2 - y1;
        int side = Math.max(cropW, cropH);
        int centerX = (x1 + x2) / 2;
        int centerY = (y1 + y2) / 2;
        x1 = Math.max(0, centerX - side / 2);
        y1 = Math.max(0, centerY - side / 2);
        x2 = Math.min(image.cols(), x1 + side);
        y2 = Math.min(image.rows(), y1 + side);

        if (x2 - x1 < side) {
            x1 = Math.max(0, x2 - side);
        }
        if (y2 - y1 < side) {
            y1 = Math.max(0, y2 - side);
        }

        Rect squareRect = new Rect(x1, y1, x2 - x1, y2 - y1);
        return new Mat(image, squareRect);
    }

    private Mat extractAlignedFace(Mat image, Point[] landmarks) {
        if (landmarks == null || landmarks.length < 3) {
            return null;
        }

        try {
            Point leftEye = landmarks[0];
            Point rightEye = landmarks[1];
            Point nose = landmarks[2];
            if (leftEye == null || rightEye == null || nose == null) {
                return null;
            }

            MatOfPoint2f src = new MatOfPoint2f(leftEye, rightEye, nose);
            MatOfPoint2f dst = new MatOfPoint2f(
                    new Point(GENDER_INPUT.width * 0.32, GENDER_INPUT.height * 0.38),
                    new Point(GENDER_INPUT.width * 0.68, GENDER_INPUT.height * 0.38),
                    new Point(GENDER_INPUT.width * 0.50, GENDER_INPUT.height * 0.60));
            Mat transform = Imgproc.getAffineTransform(src, dst);
            Mat aligned = new Mat();
            try {
                Imgproc.warpAffine(image, aligned, transform, GENDER_INPUT,
                        Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar.all(0));
                return aligned;
            } finally {
                transform.release();
                src.release();
                dst.release();
            }
        } catch (Exception e) {
            logger.debug("Aligned face extraction failed: {}", e.getMessage());
            return null;
        }
    }

    public Rect[] detectFaces(Mat image) {
        int imgWidth = image.cols();
        int imgHeight = image.rows();

        Mat blob = Dnn.blobFromImage(image, 1.0, FACE_INPUT, FACE_MEAN, false, false);
        Mat detections = null;
        try {
            synchronized (faceLock) {
                faceNet.setInput(blob);
                detections = faceNet.forward();
            }

            int numDetections = detections.size(2);
            List<Rect> faceList = new ArrayList<>(numDetections);
            for (int i = 0; i < numDetections; i++) {
                double confidence = detections.get(new int[]{0, 0, i, 2})[0];
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
                    faceList.add(new Rect(x1, y1, x2 - x1, y2 - y1));
                }
            }

            logger.debug("Found {} potential faces", faceList.size());
            return faceList.toArray(new Rect[0]);
        } finally {
            MatUtils.safeRelease(detections);
            MatUtils.safeRelease(blob);
        }
    }

    /**
     * Releases resources. OpenCV Net instances are managed by garbage collection.
     */
    public void close() {
        // OpenCV Net instances don't require explicit cleanup
        // This method exists for API consistency
    }

}
