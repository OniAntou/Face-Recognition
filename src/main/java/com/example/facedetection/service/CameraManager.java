package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import com.example.facedetection.util.MatPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages camera lifecycle, frame capture, and exposure control.
 * Encapsulates all camera-related operations.
 */
public class CameraManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CameraManager.class);
    private final AppConfig config;

    private VideoCapture capture;
    private final AtomicBoolean active;
    private int cameraIndex;
    private Consumer<Mat> frameHandler;
    private Thread captureThread;

    // Exposure control state
    private volatile long lastExposureTuneFrame;
    private volatile double currentBrightness;
    private volatile String exposureStatus;
    private volatile boolean adaptiveExposureEnabled;
    private volatile boolean brightLightModeEnabled;
    private final VisionPreprocessor visionPreprocessor;
    private MatPool matPool;

    public CameraManager() {
        this(AppConfig.getInstance());
    }

    public CameraManager(AppConfig config) {
        this.config = config;
        this.visionPreprocessor = new VisionPreprocessor(config);
        this.active = new AtomicBoolean(false);
        this.cameraIndex = 0;
        this.lastExposureTuneFrame = Long.MIN_VALUE / 4;
        this.exposureStatus = "Exposure: Ready";
        this.adaptiveExposureEnabled = true;
        this.brightLightModeEnabled = true;
        this.currentBrightness = 0.0;
    }

    public void setMatPool(MatPool matPool) {
        this.matPool = matPool;
    }

    /**
     * Opens the camera with specified index.
     *
     * @param index camera index
     * @return true if camera opened successfully
     */
    public boolean open(int index) {
        if (capture != null && capture.isOpened()) {
            close();
        }

        cameraIndex = index;
        capture = new VideoCapture();

        if (!capture.open(index)) {
            logger.error("Failed to open camera at index {}", index);
            return false;
        }

        // Configure camera properties
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, config.cameraDefaultWidth);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, config.cameraDefaultHeight);
        capture.set(Videoio.CAP_PROP_BUFFERSIZE, config.cameraBufferSize);

        // Warm up the camera
        Mat warmup = new Mat();
        for (int i = 0; i < 5; i++) {
            capture.read(warmup);
        }
        warmup.release();

        logger.info("Camera {} opened: {}x{}", index, config.cameraDefaultWidth, config.cameraDefaultHeight);
        return true;
    }

    /**
     * Starts continuous frame capture.
     *
     * @param handler callback for each captured frame
     */
    public void startCapture(Consumer<Mat> handler) {
        if (capture == null || !capture.isOpened()) {
            throw new IllegalStateException("Camera not opened");
        }

        this.frameHandler = handler;
        active.set(true);

        captureThread = new Thread(this::captureLoop, "camera-capture-" + cameraIndex);
        captureThread.setDaemon(true);
        captureThread.start();

        logger.info("Camera capture started");
    }

    /**
     * Stops frame capture.
     */
    public void stopCapture() {
        active.set(false);

        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(config.cameraShutdownTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        logger.info("Camera capture stopped");
    }

    /**
     * Single frame capture.
     *
     * @return captured frame or empty Mat if failed
     */
    public Mat grabFrame() {
        if (capture == null || !capture.isOpened()) {
            return new Mat();
        }

        Mat frame = new Mat();
        try {
            boolean success = capture.read(frame);
            if (!success || frame.empty()) {
                frame.release();
                return new Mat();
            }
            return frame;
        } catch (Exception e) {
            logger.error("Exception during frame capture: {}", e.getMessage());
            frame.release();
            return new Mat();
        }
    }

    private void captureLoop() {
        while (active.get() && !Thread.currentThread().isInterrupted()) {
            Mat frame = grabFrame();
            if (!frame.empty() && frameHandler != null) {
                // Use MatPool to recycle frames if available, otherwise clone
                Mat frameCopy;
                if (matPool != null) {
                    frameCopy = matPool.borrowByteMat();
                    frame.copyTo(frameCopy);
                } else {
                    frameCopy = frame.clone();
                }
                
                frameHandler.accept(frameCopy);
                frame.release();
            } else {
                frame.release();
            }

            try {
                Thread.sleep(config.cameraFrameIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Detects available cameras.
     *
     * @return list of available camera indices
     */
    public List<Integer> detectCameras() {
        List<Integer> available = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            VideoCapture temp = new VideoCapture(i);
            if (temp.isOpened()) {
                available.add(i);
                temp.release();
            }
        }

        logger.info("Detected {} camera(s)", available.size());
        return available;
    }

    /**
     * Sample brightness from multiple frames.
     *
     * @param framesToSample number of frames to average
     * @return average brightness
     */
    public double sampleBrightness(int framesToSample) {
        double total = 0.0;
        int validFrames = 0;

        for (int i = 0; i < framesToSample; i++) {
            Mat sample = grabFrame();
            if (!sample.empty()) {
                total += visionPreprocessor.estimateBrightness(sample);
                validFrames++;
            }
            sample.release();
        }

        return validFrames == 0 ? 0.0 : total / validFrames;
    }

    /**
     * Optimizes camera exposure for the scene.
     *
     * @param currentFrame frame number
     * @param seedFrame optional seed frame for brightness estimation
     * @return true if exposure was adjusted
     */
    public boolean optimizeExposure(long currentFrame, Mat seedFrame) {
        if (!adaptiveExposureEnabled || capture == null || !capture.isOpened()) {
            return false;
        }

        if (currentFrame - lastExposureTuneFrame < config.exposureRetuneIntervalFrames) {
            return false;
        }

        double baselineBrightness = (seedFrame != null && !seedFrame.empty())
                ? visionPreprocessor.estimateBrightness(seedFrame)
                : sampleBrightness(3);

        lastExposureTuneFrame = currentFrame;
        currentBrightness = baselineBrightness;

        if (baselineBrightness <= 0.0 || baselineBrightness <= config.cameraTargetBrightness) {
            exposureStatus = String.format("Exposure: Stable %.0f", baselineBrightness);
            return false;
        }

        return adjustExposure(baselineBrightness);
    }

    private boolean adjustExposure(double baselineBrightness) {
        boolean manualModeRequested = enableManualExposureMode();
        double originalExposure = capture.get(Videoio.CAP_PROP_EXPOSURE);
        double bestExposure = originalExposure;
        double bestBrightness = baselineBrightness;
        boolean foundImprovement = false;

        double[] candidates = buildExposureCandidates(originalExposure);

        for (double candidateExposure : candidates) {
            if (!capture.set(Videoio.CAP_PROP_EXPOSURE, candidateExposure)) {
                continue;
            }

            sleepQuietly(config.cameraExposureSettleMs);
            double candidateBrightness = sampleBrightness(2);

            if (candidateBrightness >= config.cameraMinBrightness && candidateBrightness < bestBrightness) {
                bestBrightness = candidateBrightness;
                bestExposure = candidateExposure;
                foundImprovement = true;
            }

            if (candidateBrightness <= config.cameraTargetBrightness
                    && baselineBrightness - candidateBrightness >= config.cameraMeaningfulDrop) {
                break;
            }
        }

        if (foundImprovement) {
            capture.set(Videoio.CAP_PROP_EXPOSURE, bestExposure);
            sleepQuietly(config.cameraExposureSettleMs);
            retuneGainAndWhiteBalance(bestBrightness);

            exposureStatus = String.format("Exposure: Tuned %.0f -> %.0f", baselineBrightness, bestBrightness);
            currentBrightness = bestBrightness;

            logger.info("Exposure adjusted: {} -> {} (brightness {} -> {})",
                    originalExposure, bestExposure,
                    String.format("%.1f", baselineBrightness),
                    String.format("%.1f", bestBrightness));
            return true;
        }

        if (Double.isFinite(originalExposure)) {
            capture.set(Videoio.CAP_PROP_EXPOSURE, originalExposure);
        }
        exposureStatus = manualModeRequested ? "Exposure: Driver locked" : "Exposure: Manual unsupported";
        return false;
    }

    private double[] buildExposureCandidates(double originalExposure) {
        if (!Double.isFinite(originalExposure)) {
            return new double[]{-4.0, -5.0, -6.0, -7.0};
        }
        if (originalExposure <= 0.0) {
            return new double[]{
                    originalExposure - 1.0,
                    originalExposure - 2.0,
                    originalExposure - 3.0,
                    originalExposure - 4.0
            };
        }
        return new double[]{
                Math.max(0.0, originalExposure * 0.80),
                Math.max(0.0, originalExposure * 0.65),
                Math.max(0.0, originalExposure * 0.50),
                Math.max(0.0, originalExposure * 0.35)
        };
    }

    private boolean enableManualExposureMode() {
        boolean changed = false;
        for (double mode : config.manualExposureModes) {
            try {
                if (capture.set(Videoio.CAP_PROP_AUTO_EXPOSURE, mode)) {
                    sleepQuietly(80);
                    changed = true;
                }
            } catch (Exception e) {
                logger.debug("Manual exposure mode {} not supported: {}", mode, e.getMessage());
            }
        }
        return changed;
    }

    public void enableAutomaticExposureMode() {
        if (capture == null || !capture.isOpened()) {
            return;
        }

        for (double mode : config.automaticExposureModes) {
            try {
                if (capture.set(Videoio.CAP_PROP_AUTO_EXPOSURE, mode)) {
                    sleepQuietly(80);
                    logger.debug("Requested automatic exposure mode {}", mode);
                    return;
                }
            } catch (Exception e) {
                logger.debug("Automatic exposure mode {} not supported: {}", mode, e.getMessage());
            }
        }
    }

    private void retuneGainAndWhiteBalance(double currentBrightness) {
        if (capture == null || !capture.isOpened()) {
            return;
        }

        try {
            if (currentBrightness > config.cameraTargetBrightness + 20) {
                double gain = capture.get(Videoio.CAP_PROP_GAIN);
                if (gain > 0) {
                    capture.set(Videoio.CAP_PROP_GAIN, Math.max(0, gain - 1));
                }
            }

            capture.set(Videoio.CAP_PROP_AUTO_WB, 1.0);
            sleepQuietly(50);
        } catch (Exception e) {
            logger.debug("Gain/WB tuning not supported: {}", e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Getters and setters

    public boolean isActive() {
        return active.get();
    }

    public int getCameraIndex() {
        return cameraIndex;
    }

    public String getExposureStatus() {
        return exposureStatus;
    }

    public double getCurrentBrightness() {
        return currentBrightness;
    }

    public boolean isAdaptiveExposureEnabled() {
        return adaptiveExposureEnabled;
    }

    public void setAdaptiveExposureEnabled(boolean enabled) {
        this.adaptiveExposureEnabled = enabled;
        if (!enabled) {
            enableAutomaticExposureMode();
            exposureStatus = "Exposure: Driver auto/manual hold";
        } else {
            exposureStatus = "Exposure: Assist enabled";
            lastExposureTuneFrame = Long.MIN_VALUE / 4;
        }
    }

    public boolean isBrightLightModeEnabled() {
        return brightLightModeEnabled;
    }

    public void setBrightLightModeEnabled(boolean enabled) {
        this.brightLightModeEnabled = enabled;
    }

    @Override
    public void close() {
        stopCapture();

        if (capture != null && capture.isOpened()) {
            try {
                capture.release();
            } catch (Exception e) {
                logger.error("Error releasing camera: {}", e.getMessage());
            }
        }

        logger.info("Camera manager closed");
    }
}
