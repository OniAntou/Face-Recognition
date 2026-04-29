package com.example.facedetection.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized application configuration loaded from application.properties.
 * Provides type-safe access to all configurable parameters.
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "application.properties";
    private static final AppConfig INSTANCE = new AppConfig();

    private final Properties properties;

    // Camera settings
    public final long cameraFrameIntervalMs;
    public final int cameraBufferSize;
    public final int cameraDefaultWidth;
    public final int cameraDefaultHeight;
    public final long cameraShutdownTimeoutMs;

    // Detection settings
    public final int detectionIntervalFrames;
    public final int genderPredictionInterval;
    public final int exposureRetuneIntervalFrames;
    public final double boxChangeThreshold;
    public final double minFaceSizeRatio;
    public final double smallFaceRatio;

    // YOLO settings
    public final float yoloConfidenceThreshold;
    public final float yoloNmsThreshold;
    public final int yoloFailuresBeforeCooldown;
    public final int yoloCooldownFrames;

    // SSD settings
    public final float ssdConfidenceThreshold;

    // Exposure settings
    public final double cameraTargetBrightness;
    public final double cameraMinBrightness;
    public final double cameraMeaningfulDrop;
    public final long cameraExposureSettleMs;
    public final double[] manualExposureModes;
    public final double[] automaticExposureModes;

    // Tracker settings
    public final double trackerIouThreshold;
    public final double trackerMatchThreshold;
    public final double trackerSearchExpansion;
    public final double trackerRectSmoothing;
    public final int trackerMaxMissedFrames;

    // Image settings
    public final String imageEncodingFormat;
    public final double imageEncodingQuality;
    public final int imageDisplayMaxWidth;

    // Update settings
    public final int updateCheckTimeoutSeconds;
    public final int updateDownloadTimeoutSeconds;
    public final String updateGithubApiUrl;

    // Performance settings
    public final int performanceAiThreads;
    public final int performanceAiThreadPriority;
    public final int performanceCameraThreads;

    // FPS settings
    public final double fpsSmoothingFactor;

    // Vision preprocessing settings
    public final double brightnessThreshold;
    public final double highlightRatioThreshold;
    public final double aggressiveGamma;
    public final double mildGamma;

    // FaceDetectorService color/scalar settings
    public final double[] colorBox;
    public final double[] colorLabel;
    public final double[] faceMean;
    public final double[] genderMean;
    public final int faceInputSize;
    public final int genderInputSize;

    // Haar detector settings
    public final int haarMinFaceSize;
    public final float haarDefaultConfidence;
    public final double haarScaleFactor;
    public final int haarMinNeighbors;

    // Detection pipeline filter settings
    public final double detectionMinAspectRatio;
    public final double detectionMaxAspectRatio;

    private AppConfig() {
        properties = new Properties();
        loadProperties();

        // Load all configuration values with defaults
        cameraFrameIntervalMs = getLong("camera.frame.interval.ms", 33);
        cameraBufferSize = getInt("camera.buffer.size", 1);
        cameraDefaultWidth = getInt("camera.default.width", 640);
        cameraDefaultHeight = getInt("camera.default.height", 480);
        cameraShutdownTimeoutMs = getLong("camera.shutdown.timeout.ms", 100);

        detectionIntervalFrames = getInt("detection.interval.frames", 5);
        genderPredictionInterval = getInt("detection.gender.prediction.interval", 30);
        exposureRetuneIntervalFrames = getInt("detection.exposure.retune.interval", 120);
        boxChangeThreshold = getDouble("detection.box.change.threshold", 0.15);
        minFaceSizeRatio = getDouble("detection.min.face.size.ratio", 0.07);
        smallFaceRatio = getDouble("detection.small.face.ratio", 0.045);

        yoloConfidenceThreshold = getFloat("yolo.confidence.threshold", 0.65f);
        yoloNmsThreshold = getFloat("yolo.nms.threshold", 0.45f);
        yoloFailuresBeforeCooldown = getInt("yolo.failures.before.cooldown", 3);
        yoloCooldownFrames = getInt("yolo.cooldown.frames", 24);

        ssdConfidenceThreshold = getFloat("ssd.confidence.threshold", 0.60f);

        cameraTargetBrightness = getDouble("camera.exposure.target.brightness", 165.0);
        cameraMinBrightness = getDouble("camera.exposure.min.brightness", 70.0);
        cameraMeaningfulDrop = getDouble("camera.exposure.meaningful.drop", 10.0);
        cameraExposureSettleMs = getLong("camera.exposure.settle.ms", 120);
        manualExposureModes = parseDoubleArray("camera.exposure.manual.modes", new double[]{1.0, 0.25, 0.0});
        automaticExposureModes = parseDoubleArray("camera.exposure.automatic.modes", new double[]{3.0, 0.75});

        trackerIouThreshold = getDouble("tracker.iou.threshold", 0.45);
        trackerMatchThreshold = getDouble("tracker.match.threshold", 0.58);
        trackerSearchExpansion = getDouble("tracker.search.expansion", 0.45);
        trackerRectSmoothing = getDouble("tracker.rect.smoothing", 0.60);
        trackerMaxMissedFrames = getInt("tracker.max.missed.frames", 6);

        imageEncodingFormat = getString("image.encoding.format", "png");
        imageEncodingQuality = getDouble("image.encoding.quality", 0.95);
        imageDisplayMaxWidth = getInt("image.display.max.width", 640);

        updateCheckTimeoutSeconds = getInt("update.check.timeout.seconds", 5);
        updateDownloadTimeoutSeconds = getInt("update.download.timeout.seconds", 30);
        updateGithubApiUrl = getString("update.github.api.url",
                "https://api.github.com/repos/OniAntou/Face-Recognition/releases/latest");

        performanceAiThreads = getInt("performance.ai.threads", 1);
        performanceAiThreadPriority = getInt("performance.ai.thread.priority", -1);
        performanceCameraThreads = getInt("performance.camera.threads", 1);

        fpsSmoothingFactor = getDouble("fps.smoothing.factor", 0.80);

        // Vision preprocessing settings
        brightnessThreshold = getDouble("vision.brightness.threshold", 190.0);
        highlightRatioThreshold = getDouble("vision.highlight.ratio.threshold", 0.16);
        aggressiveGamma = getDouble("vision.aggressive.gamma", 1.35);
        mildGamma = getDouble("vision.mild.gamma", 1.10);

        // FaceDetectorService settings
        colorBox = parseDoubleArray("detection.color.box", new double[]{0, 255, 0});
        colorLabel = parseDoubleArray("detection.color.label", new double[]{0, 255, 255});
        faceMean = parseDoubleArray("detection.face.mean", new double[]{104.0, 177.0, 123.0});
        genderMean = parseDoubleArray("detection.gender.mean", new double[]{78.4263377603, 87.7689143744, 114.895847746});
        faceInputSize = getInt("detection.face.input.size", 300);
        genderInputSize = getInt("detection.gender.input.size", 227);

        // Haar detector settings
        haarMinFaceSize = getInt("haar.min.face.size", 40);
        haarDefaultConfidence = getFloat("haar.default.confidence", 0.75f);
        haarScaleFactor = getDouble("haar.scale.factor", 1.1);
        haarMinNeighbors = getInt("haar.min.neighbors", 7);

        // Detection pipeline filter settings
        detectionMinAspectRatio = getDouble("detection.min.aspect.ratio", 0.5);
        detectionMaxAspectRatio = getDouble("detection.max.aspect.ratio", 1.6);

        logger.info("Configuration loaded successfully");
    }

    private void loadProperties() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                properties.load(is);
                logger.info("Loaded configuration from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
        }
    }

    private String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    private long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for {}: {}, using default {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    private float getFloat(String key, float defaultValue) {
        try {
            return Float.parseFloat(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid float value for {}: {}, using default {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for {}: {}, using default {}", key, properties.getProperty(key), defaultValue);
            return defaultValue;
        }
    }

    private double[] parseDoubleArray(String key, double[] defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            String[] parts = value.split(",");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result;
        } catch (NumberFormatException e) {
            logger.warn("Invalid double array for {}: {}, using default", key, value);
            return defaultValue;
        }
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
