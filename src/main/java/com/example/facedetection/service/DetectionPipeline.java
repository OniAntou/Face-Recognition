package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.detector.FaceDetector;
import com.example.facedetection.util.MatUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates face detection across multiple engines with fallback strategy.
 * Manages detection scheduling and result aggregation.
 */
public class DetectionPipeline implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DetectionPipeline.class);
    private final AppConfig config;

    // Detection engines (using FaceDetector interface)
    private FaceDetector yoloDetector;
    private FaceDetector ssdDetector;
    private FaceDetector haarDetector;

    // State
    private final FaceTracker tracker;
    private int yoloFailureStreak;
    private int yoloCooldownFramesRemaining;
    private long lastDetectionFrame;
    private String lastDetectorLabel;
    private volatile boolean brightLightMode;

    public DetectionPipeline(FaceDetector primaryDetector) {
        this(AppConfig.getInstance(), List.of(primaryDetector));
    }

    public DetectionPipeline(AppConfig config, List<FaceDetector> detectors) {
        this.config = config;
        this.tracker = new FaceTracker();

        // Assign detectors by type for optimal strategy
        for (FaceDetector detector : detectors) {
            String name = detector.getName();
            if (name.contains("YOLO")) {
                this.yoloDetector = detector;
            } else if (name.contains("SSD")) {
                this.ssdDetector = detector;
            } else if (name.contains("Haar")) {
                this.haarDetector = detector;
            }
        }

        this.yoloFailureStreak = 0;
        this.yoloCooldownFramesRemaining = 0;
        this.lastDetectionFrame = Long.MIN_VALUE / 4;
        this.lastDetectorLabel = "Idle";
        this.brightLightMode = false;
    }

    /**
     * Performs detection based on current frame and scheduling.
     *
     * @param frame original frame
     * @param normalizedFrame preprocessed frame for detection
     * @param currentFrame frame counter
     * @param isBrightScene whether scene is overexposed
     * @return DetectionResult containing faces and metadata
     */
    public DetectionResult process(Mat frame, Mat normalizedFrame, long currentFrame, boolean isBrightScene) {
        // Decrement cooldown
        if (yoloCooldownFramesRemaining > 0) {
            yoloCooldownFramesRemaining--;
        }

        // Decide whether to run fresh detection or tracker-only
        boolean runFreshDetection = shouldRunFreshDetection(currentFrame, isBrightScene);

        DetectionResult result;
        if (runFreshDetection) {
            result = runFreshDetectionCycle(frame, normalizedFrame, currentFrame, isBrightScene);
        } else {
            result = runTrackerOnlyCycle(frame, currentFrame, isBrightScene);
        }

        // Fallback: if tracker lost all faces, force fresh detection
        if (result.faces.isEmpty() && !runFreshDetection) {
            result = runFreshDetectionCycle(frame, normalizedFrame, currentFrame, isBrightScene);
        }

        return result;
    }

    private boolean shouldRunFreshDetection(long currentFrame, boolean brightScene) {
        if (tracker.isEmpty()) {
            return true;
        }
        if (currentFrame - lastDetectionFrame >= config.detectionIntervalFrames) {
            return true;
        }
        return brightScene && brightLightMode && currentFrame - lastDetectionFrame >= 2;
    }

    private DetectionResult runFreshDetectionCycle(Mat originalFrame, Mat normalizedFrame,
                                                    long currentFrame, boolean brightScene) {
        EngineOutcome outcome = detectWithFallbacks(originalFrame, normalizedFrame, brightScene);
        List<FaceDetection> filteredDetections = filterDetections(outcome.detections,
                originalFrame.cols(), originalFrame.rows());
        List<TrackedFace> trackedFaces = tracker.updateWithDetections(originalFrame, filteredDetections);

        lastDetectorLabel = outcome.engineLabel;
        lastDetectionFrame = currentFrame;

        return new DetectionResult(trackedFaces, outcome.engineLabel, false, outcome.confidence);
    }

    private DetectionResult runTrackerOnlyCycle(Mat originalFrame, long currentFrame, boolean brightScene) {
        List<TrackedFace> trackedFaces = tracker.track(originalFrame);

        if (trackedFaces.isEmpty()) {
            return new DetectionResult(List.of(), lastDetectorLabel, true, 0.0);
        }

        String suffix = brightScene && brightLightMode ? " · Bright-Light" : "";
        String label = "Tracker · " + lastDetectorLabel + suffix;

        return new DetectionResult(trackedFaces, label, true, trackedFaces.get(0).confidence());
    }

    private EngineOutcome detectWithFallbacks(Mat originalFrame, Mat normalizedFrame, boolean brightScene) {
        boolean preferNormalized = brightScene && brightLightMode;
        boolean preferYolo = yoloDetector != null && yoloDetector.isAvailable()
                && yoloCooldownFramesRemaining == 0
                && (brightScene || tracker.averageFaceAreaRatio(originalFrame.cols(), originalFrame.rows()) < config.smallFaceRatio);

        List<Mat> sources = preferNormalized ? List.of(normalizedFrame, originalFrame) : List.of(originalFrame, normalizedFrame);

        for (Mat source : sources) {
            String variantLabel = source == normalizedFrame && source != originalFrame
                    ? (brightScene ? "Bright-Light" : "Contrast")
                    : "Native";

            // Try YOLO first if preferred
            if (preferYolo) {
                EngineOutcome yoloOutcome = tryYolo(source, variantLabel);
                if (!yoloOutcome.detections.isEmpty()) {
                    return yoloOutcome;
                }
            }

            // Try SSD
            EngineOutcome ssdOutcome = trySsd(source, variantLabel);
            if (!ssdOutcome.detections.isEmpty()) {
                return ssdOutcome;
            }

            // Try YOLO if not preferred earlier
            if (!preferYolo) {
                EngineOutcome yoloOutcome = tryYolo(source, variantLabel);
                if (!yoloOutcome.detections.isEmpty()) {
                    return yoloOutcome;
                }
            }
        }

        // Fallback to Haar cascade
        EngineOutcome haarOutcome = tryHaar(preferNormalized ? normalizedFrame : originalFrame, preferNormalized ? "Bright-Light" : "Native");
        if (!haarOutcome.detections.isEmpty()) {
            return haarOutcome;
        }

        if (preferNormalized) {
            EngineOutcome secondaryHaar = tryHaar(originalFrame, "Native");
            if (!secondaryHaar.detections.isEmpty()) {
                return secondaryHaar;
            }
        }

        // No detections
        String status = brightScene ? "Searching (Bright-Light)" : "Searching...";
        return new EngineOutcome(List.of(), status, 0.0);
    }

    private List<FaceDetection> filterDetections(List<FaceDetection> detections, int imgWidth, int imgHeight) {
        double minSize = imgHeight * config.minFaceSizeRatio;
        List<FaceDetection> filtered = new ArrayList<>();

        for (FaceDetection d : detections) {
            Rect r = d.boundingBox();

            // Filter by size
            if (r.width < minSize || r.height < minSize) {
                continue;
            }

            // Filter by aspect ratio (faces are usually somewhat square)
            double ratio = r.width / (double) r.height;
            if (ratio < config.detectionMinAspectRatio || ratio > config.detectionMaxAspectRatio) {
                continue;
            }

            filtered.add(d);
        }

        return filtered;
    }

    private EngineOutcome tryYolo(Mat frame, String variantLabel) {
        if (yoloDetector == null || !yoloDetector.isAvailable() || yoloCooldownFramesRemaining > 0) {
            return new EngineOutcome(List.of(), "YOLO cooldown", 0.0);
        }

        List<FaceDetection> detections = yoloDetector.detect(frame);
        if (!detections.isEmpty()) {
            yoloFailureStreak = 0;
            return new EngineOutcome(detections, yoloDetector.getName() + variantSuffix(variantLabel),
                    detections.get(0).confidence());
        }

        yoloFailureStreak++;
        if (yoloFailureStreak >= config.yoloFailuresBeforeCooldown) {
            yoloCooldownFramesRemaining = config.yoloCooldownFrames;
            yoloFailureStreak = 0;
            logger.info("YOLO cooled down for {} frames after repeated misses", config.yoloCooldownFrames);
        }

        return new EngineOutcome(List.of(), "YOLO miss", 0.0);
    }

    private EngineOutcome trySsd(Mat frame, String variantLabel) {
        if (ssdDetector == null || !ssdDetector.isAvailable()) {
            return new EngineOutcome(List.of(), "SSD unavailable", 0.0);
        }

        List<FaceDetection> detections = ssdDetector.detect(frame);
        if (detections.isEmpty()) {
            return new EngineOutcome(List.of(), "SSD miss", 0.0);
        }

        return new EngineOutcome(detections, ssdDetector.getName() + variantSuffix(variantLabel), 1.0);
    }

    private EngineOutcome tryHaar(Mat frame, String variantLabel) {
        if (haarDetector == null || !haarDetector.isAvailable()) {
            return new EngineOutcome(List.of(), "Haar unavailable", 0.0);
        }

        List<FaceDetection> detections = haarDetector.detect(frame);
        if (detections.isEmpty()) {
            return new EngineOutcome(List.of(), "Haar miss", 0.0);
        }

        return new EngineOutcome(detections, haarDetector.getName() + variantSuffix(variantLabel), 0.75);
    }

    private String variantSuffix(String variantLabel) {
        return "Native".equals(variantLabel) ? "" : " · " + variantLabel;
    }

    /**
     * Resets the pipeline state.
     */
    public void reset() {
        tracker.reset();
        yoloFailureStreak = 0;
        yoloCooldownFramesRemaining = 0;
        lastDetectionFrame = Long.MIN_VALUE / 4;
        lastDetectorLabel = "Idle";
    }

    /**
     * Gets the current tracker for accessing face state.
     */
    public FaceTracker getTracker() {
        return tracker;
    }

    public void setBrightLightMode(boolean enabled) {
        this.brightLightMode = enabled;
    }

    public boolean isBrightLightMode() {
        return brightLightMode;
    }

    public String getLastDetectorLabel() {
        return lastDetectorLabel;
    }

    @Override
    public void close() {
        // Close all detectors
        if (yoloDetector != null) {
            yoloDetector.close();
        }
        if (ssdDetector != null) {
            ssdDetector.close();
        }
        if (haarDetector != null) {
            haarDetector.close();
        }

        tracker.close();
        logger.info("Detection pipeline closed");
    }

    // Records

    public record DetectionResult(List<TrackedFace> faces, String engineLabel, boolean trackerOnly, double confidence) {}

    private record EngineOutcome(List<FaceDetection> detections, String engineLabel, double confidence) {}
}
