package com.example.facedetection.controller;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.config.ModelPaths;
import com.example.facedetection.detector.*;
import com.example.facedetection.metrics.FpsCalculator;
import com.example.facedetection.processor.FrameProcessor;
import com.example.facedetection.service.*;
import com.example.facedetection.ui.UIManager;
import com.example.facedetection.util.MatPool;
import com.example.facedetection.util.MatUtils;
import com.example.facedetection.util.PathValidator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main UI controller for Face Recognition application.
 * Refactored to delegate business logic to specialized services.
 */
public class ViewController {

    private static final Logger logger = LoggerFactory.getLogger(ViewController.class);
    private final AppConfig config = AppConfig.getInstance();

    // UI Components
    @FXML private ImageView originalImageView;
    @FXML private ImageView processedImageView;
    @FXML private StackPane sourceViewport;
    @FXML private StackPane resultViewport;
    @FXML private VBox sourceCard;
    @FXML private Button cameraButton;
    @FXML private ComboBox<String> cameraSelector;
    @FXML private Label statusLabel;
    @FXML private Label engineLabel;
    @FXML private Label fpsLabel;
    @FXML private Label exposureStatusLabel;
    @FXML private CheckBox adaptiveExposureCheckBox;
    @FXML private CheckBox brightLightModeCheckBox;
    @FXML private CheckBox genderRecognitionCheckBox;

    // Services
    private FaceDetector yoloDetector;
    private FaceDetector ssdDetector;
    private FaceDetector haarDetector;
    private FaceDetectorService faceDetectorService; // For gender prediction only
    private CameraManager cameraManager;
    private DetectionPipeline detectionPipeline;
    private UpdateService updateService;
    private PreferencesService preferencesService;

    // Object pool for Mat recycling
    private MatPool matPool;

    // Threading
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final ExecutorService aiExecutor;

    // Processing services
    private FrameProcessor frameProcessor;
    private FpsCalculator fpsCalculator;
    private UIManager uiManager;

    // State
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final AtomicBoolean adaptiveExposureEnabled = new AtomicBoolean(true);
    private final AtomicBoolean brightLightModeEnabled = new AtomicBoolean(true);
    private final AtomicBoolean genderRecognitionEnabled = new AtomicBoolean(false);

    public ViewController() {
        // Configure AI executor from config
        this.aiExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ai-processor");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY,
                    Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + config.performanceAiThreadPriority)));
            return thread;
        });
    }

    public void initialize() {
        try {
            // Initialize Mat pool
            matPool = new MatPool(20, 10);

            // Initialize preferences
            preferencesService = new PreferencesService();

            // Initialize AI models
            initializeModels();

            // Initialize services
            cameraManager = new CameraManager();
            cameraManager.setMatPool(matPool);

            // Build list of available detectors
            List<FaceDetector> detectors = new ArrayList<>();
            if (yoloDetector != null && yoloDetector.isAvailable()) detectors.add(yoloDetector);
            if (ssdDetector != null && ssdDetector.isAvailable()) detectors.add(ssdDetector);
            if (haarDetector != null && haarDetector.isAvailable()) detectors.add(haarDetector);

            detectionPipeline = new DetectionPipeline(config, detectors);
            updateService = new UpdateService();

            // Initialize processing services
            frameProcessor = new FrameProcessor(config, detectionPipeline, faceDetectorService, matPool);
            fpsCalculator = new FpsCalculator(config);
            uiManager = new UIManager(originalImageView, processedImageView, sourceViewport, resultViewport,
                    sourceCard, cameraButton, cameraSelector, statusLabel, engineLabel, fpsLabel,
                    exposureStatusLabel, adaptiveExposureCheckBox, brightLightModeCheckBox,
                    genderRecognitionCheckBox,
                    cameraManager, updateService);

            // Restore preferences
            restorePreferences();

            // Detect available cameras
            detectCameras();

            // Check for updates
            checkForUpdates();

            logger.info("ViewController initialized successfully");
        } catch (Exception e) {
            logger.error("Initialization error", e);
            if (uiManager != null) {
                uiManager.showError("Initialization Error", "Could not load AI models: " + e.getMessage());
            }
        }
    }

    private void initializeModels() {
        ModelPaths modelPaths = ModelPaths.resolve();
        logger.info("Resolved model data directory: {}", modelPaths.dataDirectory());
        if (!modelPaths.missingFiles().isEmpty()) {
            logger.warn(modelPaths.describeMissingFiles());
        }

        // Initialize YOLO if available
        if (modelPaths.hasYolo() && PathValidator.isValidModelPath(modelPaths.yoloModel().toString())) {
            try {
                yoloDetector = new YoloFaceDetector(modelPaths.yoloModel().toString(),
                        config.yoloConfidenceThreshold, config.yoloNmsThreshold);
                logger.info("YOLO detector initialized from {}", modelPaths.yoloModel());
            } catch (Exception e) {
                logger.error("Failed to initialize YOLO from {}: {}", modelPaths.yoloModel(), e.getMessage());
            }
        }

        // Initialize SSD
        if (modelPaths.hasSsd()) {
            try {
                ssdDetector = new SsdFaceDetector(
                        modelPaths.ssdModel().toString(),
                        modelPaths.ssdConfig().toString(),
                        config.ssdConfidenceThreshold,
                        config);
                logger.info("SSD detector initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize SSD: {}", e.getMessage());
            }
        }

        // Keep FaceDetectorService for gender prediction
        if (modelPaths.hasSsd() && modelPaths.hasGender()) {
            try {
                faceDetectorService = new FaceDetectorService(
                        modelPaths.ssdModel().toString(),
                        modelPaths.ssdConfig().toString(),
                        modelPaths.genderModel().toString(),
                        modelPaths.genderConfig().toString(),
                        config.ssdConfidenceThreshold,
                        config);
                logger.info("Gender recognition service initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize gender recognition: {}", e.getMessage());
            }
        }

        // Initialize Haar cascade if available
        if (modelPaths.hasHaar() && PathValidator.isValidModelPath(modelPaths.haarCascade().toString())) {
            try {
                haarDetector = new HaarFaceDetector(modelPaths.haarCascade().toString());
                logger.info("Haar cascade detector initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize Haar cascade: {}", e.getMessage());
            }
        }

        if (!modelPaths.hasAnyFaceDetector()) {
            logger.warn("No face detector model files were found. Application may not function correctly.");
        }
    }

    private void restorePreferences() {
        // Restore exposure settings
        boolean adaptiveExposure = preferencesService.isAdaptiveExposureEnabled(true);
        adaptiveExposureEnabled.set(adaptiveExposure);
        if (adaptiveExposureCheckBox != null) {
            adaptiveExposureCheckBox.setSelected(adaptiveExposure);
        }

        boolean brightLightMode = preferencesService.isBrightLightModeEnabled(true);
        brightLightModeEnabled.set(brightLightMode);
        if (brightLightModeCheckBox != null) {
            brightLightModeCheckBox.setSelected(brightLightMode);
        }
        if (detectionPipeline != null) {
            detectionPipeline.setBrightLightMode(brightLightMode);
        }
        if (cameraManager != null) {
            cameraManager.setBrightLightModeEnabled(brightLightMode);
        }

        boolean genderRecognition = preferencesService.isGenderRecognitionEnabled(false);
        boolean genderAvailable = faceDetectorService != null;
        genderRecognitionEnabled.set(genderAvailable && genderRecognition);
        if (genderRecognitionCheckBox != null) {
            genderRecognitionCheckBox.setDisable(!genderAvailable);
            genderRecognitionCheckBox.setSelected(genderAvailable && genderRecognition);
            if (!genderAvailable) {
                genderRecognitionCheckBox.setTooltip(new javafx.scene.control.Tooltip(
                        "Gender model is unavailable"));
            }
        }

        // Initialize labels
        if (fpsLabel != null) {
            fpsLabel.setText("FPS: --");
        }
        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText("Exposure: Ready");
        }

        logger.debug("Preferences restored");
    }

    @FXML
    private void selectImage() {
        if (cameraManager.isActive()) {
            stopCamera();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = fileChooser.showOpenDialog(originalImageView.getScene().getWindow());
        if (selectedFile != null && PathValidator.isValidImagePath(selectedFile.getAbsolutePath())) {
            processSelectedImage(selectedFile);
        } else if (selectedFile != null) {
            uiManager.showError("Invalid File", "Selected file is not a valid image");
        }
    }

    @FXML
    private void toggleCamera() {
        if (!cameraManager.isActive()) {
            startCamera();
        } else {
            stopCamera();
        }
    }

    @FXML
    private void handleAdaptiveExposureToggle() {
        boolean enabled = isAdaptiveExposureEnabled();
        adaptiveExposureEnabled.set(enabled);
        cameraManager.setAdaptiveExposureEnabled(enabled);
        preferencesService.setAdaptiveExposureEnabled(enabled);

        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText(cameraManager.getExposureStatus());
        }
    }

    @FXML
    private void handleBrightLightModeToggle() {
        boolean enabled = isBrightLightModeEnabled();
        brightLightModeEnabled.set(enabled);
        if (detectionPipeline != null) {
            detectionPipeline.setBrightLightMode(enabled);
        }
        if (cameraManager != null) {
            cameraManager.setBrightLightModeEnabled(enabled);
        }
        preferencesService.setBrightLightModeEnabled(enabled);

        if (engineLabel != null && !cameraManager.isActive()) {
            engineLabel.setText(enabled ? "Engine: Bright-Light Ready" : "Engine: Standard");
        }
    }

    @FXML
    private void handleGenderRecognitionToggle() {
        boolean enabled = faceDetectorService != null && isGenderRecognitionEnabled();
        genderRecognitionEnabled.set(enabled);
        preferencesService.setGenderRecognitionEnabled(enabled);
    }

    private void startCamera() {
        // Get selected camera index
        int cameraIndex = uiManager.getSelectedCameraIndex();

        // Save preference
        preferencesService.setLastCameraIndex(cameraIndex);

        // Open camera
        if (!cameraManager.open(cameraIndex)) {
            uiManager.showError("Camera Error", "Could not open camera device at index " + cameraIndex);
            return;
        }

        // Reset processing state
        frameProcessor.reset();
        fpsCalculator.reset();

        // Configure initial exposure
        cameraManager.setAdaptiveExposureEnabled(adaptiveExposureEnabled.get());

        // Update UI
        uiManager.setCameraActive();

        // Start capture with frame processor
        cameraManager.startCapture(this::onFrameCaptured);
        logger.info("Camera {} started", cameraIndex);
    }

    private void stopCamera() {
        cameraManager.stopCapture();

        // Update UI
        uiManager.setCameraInactive();

        // Reset processing state
        frameProcessor.reset();

        logger.info("Camera stopped");
    }

    private void onFrameCaptured(Mat frame) {
        if (!processing.compareAndSet(false, true)) {
            if (matPool != null) {
                matPool.returnMat(frame);
            } else {
                frame.release();
            }
            return;
        }

        long currentFrame = frameCounter.incrementAndGet();
        // Snapshot controller state before dispatching work. JavaFX controls
        // must never be queried from the AI executor.
        boolean brightLightMode = brightLightModeEnabled.get();
        boolean genderRecognition = genderRecognitionEnabled.get();

        // The frame passed from CameraManager is already a clone/safe copy.
        // We can pass it directly to the processor.
        CompletableFuture.supplyAsync(() -> {
            try {
                FrameProcessor.FrameResult result = frameProcessor.process(
                        frame, 
                        currentFrame, 
                        brightLightMode,
                        genderRecognition);
                double fps = fpsCalculator.update();
                return new FrameUIResult(result, fps, cameraManager.getExposureStatus());
            } finally {
                // Important: Return the frame to pool instead of releasing
                if (matPool != null) {
                    matPool.returnMat(frame);
                } else {
                    frame.release();
                }
            }
        }, aiExecutor)
                .thenAcceptAsync(this::updateUI, Platform::runLater)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Frame processing failed", error);
                    }
                    processing.set(false);
                });
    }

    private record FrameUIResult(FrameProcessor.FrameResult frameResult, double fps, String exposureStatus) {}

    private void updateUI(FrameUIResult result) {
        uiManager.updateFrame(
                result.frameResult().originalImage(),
                result.frameResult().processedImage(),
                "Live - " + result.frameResult().statusText(),
                "Engine: " + result.frameResult().engineLabel(),
                result.fps(),
                result.exposureStatus()
        );
    }

    private void processSelectedImage(File file) {
        if (!PathValidator.isValidImagePath(file.getAbsolutePath())) {
            uiManager.showError("Invalid File", "Selected file is not a valid image");
            return;
        }

        boolean brightLightMode = brightLightModeEnabled.get();
        boolean genderRecognition = genderRecognitionEnabled.get();
        CompletableFuture.supplyAsync(() -> {
            Mat image = Imgcodecs.imread(file.getAbsolutePath());
            if (image.empty()) {
                MatUtils.safeRelease(image);
                throw new IllegalArgumentException("Cannot read image at " + file.getAbsolutePath());
            }

            try {
                // Reset for fresh detection
                frameProcessor.reset();

                return frameProcessor.process(image, 1, brightLightMode, genderRecognition);

            } finally {
                MatUtils.safeRelease(image);
            }
        }, aiExecutor).thenAcceptAsync(result -> uiManager.updateStaticImage(
                result.originalImage(), result.processedImage(),
                "Done - " + result.statusText(), "Engine: " + result.engineLabel()), Platform::runLater)
                .exceptionally(error -> {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    Platform.runLater(() -> uiManager.showError("Image Error", cause.getMessage()));
                    logger.error("Selected image processing failed", cause);
                    return null;
                });
    }

    private void detectCameras() {
        List<Integer> availableCameras = cameraManager.detectCameras();
        List<String> cameraNames = new ArrayList<>();

        for (int i : availableCameras) {
            cameraNames.add("Camera " + i);
        }

        if (cameraNames.isEmpty()) {
            cameraNames.add("No Camera Found");
        }

        uiManager.updateCameraSelector(cameraNames);

        // Select last used camera if available
        int lastCamera = preferencesService.getLastCameraIndex(0);
        uiManager.selectCamera(lastCamera);

        logger.info("Detected {} camera(s)", availableCameras.size());
    }

    private void checkForUpdates() {
        updateService.checkForUpdates(
                url -> Platform.runLater(() -> {
                    uiManager.showUpdateNotification("New Update Found (Click to install)",
                            () -> downloadAndInstallUpdate(url));
                }),
                error -> logger.debug("Update check failed: {}", error)
        );
    }

    private void downloadAndInstallUpdate(String url) {
        statusLabel.setText("Downloading update... please wait");
        statusLabel.setDisable(true);

        updateService.downloadAndInstall(url,
                progress -> {
                    // Progress updates could be shown here
                    logger.debug("Download progress: {}%", progress);
                },
                () -> {
                    logger.info("Update ready, exiting application");
                    Platform.exit();
                },
                error -> Platform.runLater(() -> {
                    String detail = error == null || error.isBlank() ? "Unknown error" : error;
                    statusLabel.setText("Update failed: " + detail);
                    statusLabel.setDisable(false);
                    logger.error("Update failed: {}", detail);
                })
        );
    }

    /**
     * Graceful shutdown of all resources.
     */
    public void shutdown() {
        logger.info("Shutting down ViewController...");

        // Stop camera
        if (cameraManager != null) {
            cameraManager.close();
        }

        // Shutdown AI executor gracefully
        if (aiExecutor != null) {
            aiExecutor.shutdown();
            try {
                if (!aiExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("AI executor did not terminate in time, forcing shutdown");
                    aiExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                aiExecutor.shutdownNow();
            }
        }

        // Close services
        if (frameProcessor != null) {
            frameProcessor.close();
        }
        if (detectionPipeline != null) {
            detectionPipeline.close();
        }
        if (faceDetectorService != null) {
            faceDetectorService.close();
        }
        if (matPool != null) {
            matPool.close();
        }

        logger.info("ViewController shutdown complete");
    }

    // Helper methods for checkbox state checking
    private boolean isAdaptiveExposureEnabled() {
        return adaptiveExposureCheckBox == null || adaptiveExposureCheckBox.isSelected();
    }

    private boolean isBrightLightModeEnabled() {
        return brightLightModeCheckBox == null || brightLightModeCheckBox.isSelected();
    }

    private boolean isGenderRecognitionEnabled() {
        return genderRecognitionCheckBox != null && !genderRecognitionCheckBox.isDisabled()
                && genderRecognitionCheckBox.isSelected();
    }
}
