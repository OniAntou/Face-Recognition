package com.example.facedetection.processor;

import com.example.facedetection.cache.GenderCacheManager;
import com.example.facedetection.config.AppConfig;
import com.example.facedetection.service.DetectionPipeline;
import com.example.facedetection.service.FaceDetectorService;
import com.example.facedetection.service.TrackedFace;
import com.example.facedetection.service.VisionPreprocessor;
import com.example.facedetection.util.MatPool;
import com.example.facedetection.util.MatUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes video frames for face detection and annotation.
 * Coordinates between detection pipeline, caching, and image encoding.
 */
public class FrameProcessor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FrameProcessor.class);
    private final AppConfig config;
    private final DetectionPipeline detectionPipeline;
    private final FaceDetectorService faceDetectorService;
    private final GenderCacheManager genderCache;
    private final MatPool matPool;
    private final VisionPreprocessor visionPreprocessor;

    // State
    private final Map<Integer, Long> lastGenderPredictionFrame = new ConcurrentHashMap<>();
    private final Map<Integer, Rect> lastGenderPredictionBox = new ConcurrentHashMap<>();

    public FrameProcessor(AppConfig config, DetectionPipeline detectionPipeline,
                          FaceDetectorService faceDetectorService, MatPool matPool) {
        this.config = config;
        this.detectionPipeline = detectionPipeline;
        this.faceDetectorService = faceDetectorService;
        this.matPool = matPool;
        this.genderCache = new GenderCacheManager(config);
        this.visionPreprocessor = new VisionPreprocessor(config);
    }

    /**
     * Processes a single frame through the detection pipeline.
     *
     * @param frame the input frame
     * @param currentFrame frame counter
     * @param isBrightLightMode whether bright light mode is enabled
     * @param isGenderRecognitionEnabled whether gender recognition is enabled
     * @return processed frame result with image and metadata
     */
    public FrameResult process(Mat frame, long currentFrame, boolean isBrightLightMode, boolean isGenderRecognitionEnabled) {
        Mat displayFrame = null;
        Mat normalizedFrame = null;
        Mat processedFrame = null;

        try {
            // Prepare display frame from pool
            displayFrame = prepareDisplayFrame(frame);

            // Check for bright scene
            boolean isBrightScene = visionPreprocessor.isLikelyOverexposed(displayFrame);

            // Prepare normalized frame if bright light mode enabled
            if (isBrightLightMode && isBrightScene) {
                normalizedFrame = visionPreprocessor.prepareForFaceDetection(displayFrame);
            } else {
                // Borrow from pool instead of cloning
                normalizedFrame = matPool.borrowByteMat();
                displayFrame.copyTo(normalizedFrame);
            }

            // Run detection pipeline
            DetectionPipeline.DetectionResult detectionResult = detectionPipeline.process(
                    displayFrame, normalizedFrame, currentFrame, isBrightScene);

            // Create output frame with annotations
            processedFrame = matPool.borrowByteMat();
            displayFrame.copyTo(processedFrame);
            drawAnnotations(processedFrame, normalizedFrame, detectionResult.faces(), currentFrame, isGenderRecognitionEnabled);

            // Build status text
            String statusText = detectionResult.faces().size() + " face" +
                    (detectionResult.faces().size() != 1 ? "s" : "") + " detected";

            return new FrameResult(
                    matToImage(displayFrame),
                    matToImage(processedFrame),
                    statusText,
                    detectionResult.engineLabel(),
                    isBrightScene
            );

        } finally {
            matPool.returnMat(displayFrame);
            matPool.returnMat(normalizedFrame);
            matPool.returnMat(processedFrame);
        }
    }

    /**
     * Resets all processing state.
     */
    public void reset() {
        detectionPipeline.reset();
        genderCache.clear();
        lastGenderPredictionFrame.clear();
        lastGenderPredictionBox.clear();
    }

    private Mat prepareDisplayFrame(Mat frame) {
        Mat prepared = matPool.borrowByteMat();

        if (frame.cols() > config.imageDisplayMaxWidth) {
            double scale = (double) config.imageDisplayMaxWidth / frame.cols();
            org.opencv.core.Size newSize = new org.opencv.core.Size(config.imageDisplayMaxWidth,
                    frame.rows() * scale);
            org.opencv.imgproc.Imgproc.resize(frame, prepared, newSize);
        } else {
            frame.copyTo(prepared);
        }

        // Convert to BGR if needed
        if (prepared.channels() == 4) {
            Mat bgr = matPool.borrowByteMat();
            org.opencv.imgproc.Imgproc.cvtColor(prepared, bgr, org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR);
            matPool.returnMat(prepared);
            return bgr;
        }
        if (prepared.channels() == 1) {
            Mat bgr = matPool.borrowByteMat();
            org.opencv.imgproc.Imgproc.cvtColor(prepared, bgr, org.opencv.imgproc.Imgproc.COLOR_GRAY2BGR);
            matPool.returnMat(prepared);
            return bgr;
        }

        return prepared;
    }

    private void drawAnnotations(Mat outputFrame, Mat analysisFrame,
                                   List<TrackedFace> faces, long currentFrame, boolean isGenderRecognitionEnabled) {
        Set<Integer> activeIds = ConcurrentHashMap.newKeySet();

        for (TrackedFace face : faces) {
            activeIds.add(face.id());
            Rect rect = face.boundingBox();

            // Draw bounding box
            org.opencv.imgproc.Imgproc.rectangle(outputFrame, rect.tl(), rect.br(),
                    new org.opencv.core.Scalar(0, 255, 0), 2);

            // Determine if gender prediction needed
            if (isGenderRecognitionEnabled && shouldPredictGender(face, currentFrame, rect)) {
                String[] result = faceDetectorService.predictGender(
                        analysisFrame, rect, face.landmarksCopy());
                genderCache.put(face.id(), result);
                lastGenderPredictionFrame.put(face.id(), currentFrame);
                lastGenderPredictionBox.put(face.id(), new Rect(rect.x, rect.y, rect.width, rect.height));
            }

            // Draw gender label if enabled
            if (isGenderRecognitionEnabled) {
                String[] genderInfo = genderCache.get(face.id());
                if (genderInfo != null) {
                    String label = genderInfo[0] + " " + genderInfo[1];
                    org.opencv.imgproc.Imgproc.putText(outputFrame, label,
                            new org.opencv.core.Point(rect.x, rect.y - 10),
                            org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                            new org.opencv.core.Scalar(0, 255, 255), 2);
                }
            }
        }

        // Prune old entries
        genderCache.retainAll(activeIds);
        lastGenderPredictionFrame.keySet().retainAll(activeIds);
        lastGenderPredictionBox.keySet().retainAll(activeIds);
    }

    private boolean shouldPredictGender(TrackedFace face, long currentFrame, Rect rect) {
        long lastPrediction = lastGenderPredictionFrame.getOrDefault(face.id(), Long.MIN_VALUE / 4);
        Rect lastBox = lastGenderPredictionBox.get(face.id());

        boolean hasCache = genderCache.contains(face.id());
        boolean boxChanged = lastBox == null || computeBoxChange(lastBox, rect) > config.boxChangeThreshold;
        boolean intervalPassed = (currentFrame - lastPrediction) >= config.genderPredictionInterval;

        return !hasCache || (intervalPassed && boxChanged);
    }

    private double computeBoxChange(Rect oldBox, Rect newBox) {
        double dw = Math.abs(oldBox.width - newBox.width) / (double) Math.max(1, oldBox.width);
        double dh = Math.abs(oldBox.height - newBox.height) / (double) Math.max(1, oldBox.height);
        double dx = Math.abs(oldBox.x - newBox.x) / (double) Math.max(1, oldBox.width);
        double dy = Math.abs(oldBox.y - newBox.y) / (double) Math.max(1, oldBox.height);
        return (dw + dh + dx + dy) / 4.0;
    }

    private Image matToImage(Mat frame) {
        return MatUtils.matToImageDirect(frame);
    }

    @Override
    public void close() {
        genderCache.clear();
        lastGenderPredictionFrame.clear();
        lastGenderPredictionBox.clear();
        logger.info("FrameProcessor closed");
    }

    // Record for frame processing results
    public record FrameResult(Image originalImage, Image processedImage, String statusText,
                              String engineLabel, boolean isBrightScene) {}
}
