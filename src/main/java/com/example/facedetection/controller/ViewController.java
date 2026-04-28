package com.example.facedetection.controller;

import com.example.facedetection.service.FaceDetection;
import com.example.facedetection.service.FaceDetectorService;
import com.example.facedetection.service.FaceTracker;
import com.example.facedetection.service.TrackedFace;
import com.example.facedetection.service.VisionPreprocessor;
import com.example.facedetection.service.YoloFaceService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Manifest;

public class ViewController {

    private static final Logger logger = LoggerFactory.getLogger(ViewController.class);

    private static final long FRAME_INTERVAL_MS = 33;
    private static final long SHUTDOWN_TIMEOUT_MS = 100;
    private static final int DETECTION_INTERVAL_FRAMES = 5;
    private static final int GENDER_PREDICTION_INTERVAL = 30;
    private static final int EXPOSURE_RETUNE_INTERVAL_FRAMES = 120;
    private static final double BOX_CHANGE_THRESHOLD = 0.15;
    private static final double MIN_FACE_SIZE_RATIO = 0.07;
    private static final int YOLO_FAILURES_BEFORE_COOLDOWN = 3;
    private static final int YOLO_COOLDOWN_FRAMES = 24;
    private static final double SMALL_FACE_RATIO = 0.045;
    private static final double CAMERA_TARGET_BRIGHTNESS = 165.0;
    private static final double CAMERA_MIN_BRIGHTNESS = 70.0;
    private static final double CAMERA_MEANINGFUL_DROP = 10.0;
    private static final long CAMERA_EXPOSURE_SETTLE_MS = 120;
    private static final double[] MANUAL_EXPOSURE_MODES = {1.0, 0.25, 0.0};
    private static final double[] AUTOMATIC_EXPOSURE_MODES = {3.0, 0.75};

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

    private org.opencv.objdetect.CascadeClassifier haarCascade;
    private FaceDetectorService faceDetectorService;
    private YoloFaceService yoloFaceService;
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> cameraTask;
    private volatile boolean cameraActive = false;
    private volatile boolean updateFound = false;
    private volatile String exposureStatusText = "Exposure: Ready";
    private volatile String lastDetectorLabel = "Idle";
    private volatile long lastExposureTuneFrame = Long.MIN_VALUE / 4;
    private volatile long lastDetectionFrame = Long.MIN_VALUE / 4;
    private volatile int yoloFailureStreak = 0;
    private volatile int yoloCooldownFramesRemaining = 0;

    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ai-processor");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final AtomicLong lastFpsTimestampNanos = new AtomicLong(0);
    private final Map<Integer, String[]> genderCache = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastGenderPredictionFrame = new ConcurrentHashMap<>();
    private final Map<Integer, Rect> lastGenderPredictionBox = new ConcurrentHashMap<>();
    private final MatOfByte encodingBuffer = new MatOfByte();
    private final FaceTracker faceTracker = new FaceTracker();
    private double smoothedFps = 0.0;

    private record DetectionCycleResult(List<TrackedFace> faces, String engineLabel, boolean trackerOnly) {}

    private record DetectorOutcome(List<FaceDetection> detections, String engineLabel) {}

    public void initialize() {
        try {
            String faceModelFile = "res10_300x300_ssd_iter_140000.caffemodel";
            String faceConfigFile = "deploy.prototxt";
            String genderModelFile = "gender_net.caffemodel";
            String genderConfigFile = "gender_deploy.prototxt";
            String yoloModelFile = "yolov8n-face.onnx";

            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir = new File("app" + File.separator + "data");
            }

            File yoloFile = new File(dataDir, yoloModelFile);
            if (yoloFile.exists()) {
                logger.info("YOLO model detected, using YOLO for detection.");
                try {
                    yoloFaceService = new YoloFaceService(yoloFile.getAbsolutePath(), 0.65f, 0.45f);
                } catch (Exception e) {
                    logger.error("Failed to initialize ONNX Runtime: {}", e.getMessage());
                    yoloFaceService = null;
                }
            }

            faceDetectorService = new FaceDetectorService(
                    new File(dataDir, faceModelFile).getAbsolutePath(),
                    new File(dataDir, faceConfigFile).getAbsolutePath(),
                    new File(dataDir, genderModelFile).getAbsolutePath(),
                    new File(dataDir, genderConfigFile).getAbsolutePath(),
                    0.60f);

            File haarFile = new File(dataDir, "haarcascade_frontalface_default.xml");
            if (haarFile.exists()) {
                haarCascade = new org.opencv.objdetect.CascadeClassifier(haarFile.getAbsolutePath());
            }

            capture = new VideoCapture();
            bindImageViewToParent(originalImageView, sourceViewport);
            bindImageViewToParent(processedImageView, resultViewport);
            if (adaptiveExposureCheckBox != null) {
                adaptiveExposureCheckBox.setSelected(true);
            }
            if (brightLightModeCheckBox != null) {
                brightLightModeCheckBox.setSelected(true);
            }
            if (fpsLabel != null) {
                fpsLabel.setText("FPS: --");
            }
            if (exposureStatusLabel != null) {
                exposureStatusLabel.setText(exposureStatusText);
            }

            detectCameras();
            checkUpdates();
        } catch (Exception e) {
            logger.error("Initialization Error", e);
            showError("Initialization Error", "Could not load AI models: " + e.getMessage());
        }
    }

    @FXML
    private void selectImage() {
        if (cameraActive) {
            stopCamera();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));

        File selectedFile = fileChooser.showOpenDialog(originalImageView.getScene().getWindow());
        if (selectedFile != null) {
            processSelectedImage(selectedFile);
        }
    }

    @FXML
    private void toggleCamera() {
        if (!cameraActive) {
            startCamera();
        } else {
            stopCamera();
        }
    }

    @FXML
    private void handleAdaptiveExposureToggle() {
        if (isAdaptiveExposureEnabled()) {
            exposureStatusText = "Exposure: Assist enabled";
            lastExposureTuneFrame = Long.MIN_VALUE / 4;
        } else {
            tryEnableAutomaticExposureMode();
            exposureStatusText = "Exposure: Driver auto/manual hold";
        }

        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText(exposureStatusText);
        }
    }

    @FXML
    private void handleBrightLightModeToggle() {
        if (engineLabel != null && !cameraActive) {
            engineLabel.setText(isBrightLightModeEnabled() ? "Engine: Bright-Light Ready" : "Engine: Standard");
        }
    }

    private void startCamera() {
        int cameraIndex = 0;
        String selected = cameraSelector.getSelectionModel().getSelectedItem();
        if (selected != null && selected.contains("Camera ")) {
            try {
                cameraIndex = Integer.parseInt(selected.replace("Camera ", ""));
            } catch (NumberFormatException ignored) {
                // Keep default index 0.
            }
        }

        capture.open(cameraIndex);
        if (!capture.isOpened()) {
            showError("Camera Error", "Could not open camera device at index " + cameraIndex);
            return;
        }

        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
        capture.set(Videoio.CAP_PROP_BUFFERSIZE, 1);

        faceTracker.reset();
        genderCache.clear();
        lastGenderPredictionFrame.clear();
        lastDetectorLabel = "Starting";
        exposureStatusText = "Exposure: Warming up";
        if (isAdaptiveExposureEnabled()) {
            optimizeCameraExposureIfSupported(0, null);
        }

        cameraActive = true;
        cameraButton.setText("\u23F9  Stop Camera");
        cameraButton.getStyleClass().remove("btn-primary");
        cameraButton.getStyleClass().add("btn-danger");
        sourceCard.setVisible(false);
        sourceCard.setManaged(false);

        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "camera-capture");
            thread.setDaemon(true);
            return thread;
        });
        cameraTask = timer.scheduleAtFixedRate(this::captureAndProcessFrame, 0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void captureAndProcessFrame() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        Mat frame = grabFrame();
        if (frame == null || frame.empty()) {
            processing.set(false);
            safeRelease(frame);
            return;
        }

        long currentFrame = frameCounter.incrementAndGet();
        if (yoloCooldownFramesRemaining > 0) {
            yoloCooldownFramesRemaining--;
        }
        if (shouldRetuneExposure(currentFrame, frame)) {
            optimizeCameraExposureIfSupported(currentFrame, frame);
        }

        CompletableFuture.supplyAsync(() -> processFrame(frame, currentFrame), aiExecutor)
                .thenAcceptAsync(result -> {
                    if (!cameraActive) {
                        return;
                    }

                    originalImageView.setImage(result.originalImage);
                    processedImageView.setImage(result.processedImage);
                    if (!updateFound) {
                        statusLabel.setText(result.statusText);
                    }
                    if (engineLabel != null) {
                        engineLabel.setText("Engine: " + result.engineText);
                    }
                    if (fpsLabel != null) {
                        fpsLabel.setText(String.format("FPS: %.1f", result.fps));
                    }
                    if (exposureStatusLabel != null) {
                        exposureStatusLabel.setText(exposureStatusText);
                    }
                }, Platform::runLater)
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        logger.error("Frame processing failed", error);
                    }
                    safeRelease(frame);
                    processing.set(false);
                });
    }

    private FrameRenderResult processFrame(Mat frame, long currentFrame) {
        Mat originalDisplayFrame = new Mat();
        Mat processedFrame = new Mat();
        Mat normalizedFrame = new Mat();
        try {
            originalDisplayFrame = prepareDisplayFrame(frame);
            originalDisplayFrame.copyTo(processedFrame);

            boolean brightScene = VisionPreprocessor.isLikelyOverexposed(originalDisplayFrame);
            normalizedFrame = (isBrightLightModeEnabled() && brightScene)
                    ? VisionPreprocessor.prepareForFaceDetection(originalDisplayFrame)
                    : originalDisplayFrame.clone();

            DetectionCycleResult cycleResult = shouldRunFreshDetection(currentFrame, brightScene)
                    ? runFreshDetectionCycle(originalDisplayFrame, normalizedFrame, currentFrame, brightScene)
                    : runTrackerOnlyCycle(originalDisplayFrame, normalizedFrame, currentFrame, brightScene);

            if (cycleResult.faces.isEmpty() && cycleResult.trackerOnly) {
                cycleResult = runFreshDetectionCycle(originalDisplayFrame, normalizedFrame, currentFrame, brightScene);
            }

            drawFaceAnnotations(processedFrame, normalizedFrame, cycleResult.faces, currentFrame);
            String statusText = "Live \u00B7 " + cycleResult.faces.size() + " face" +
                    (cycleResult.faces.size() != 1 ? "s" : "") + " detected";
            double fps = updateFps();

            return new FrameRenderResult(
                    mat2Image(originalDisplayFrame),
                    mat2Image(processedFrame),
                    statusText,
                    cycleResult.engineLabel,
                    fps);
        } finally {
            safeRelease(originalDisplayFrame);
            safeRelease(processedFrame);
            safeRelease(normalizedFrame);
        }
    }

    private DetectionCycleResult runFreshDetectionCycle(Mat originalFrame, Mat normalizedFrame,
                                                        long currentFrame, boolean brightScene) {
        DetectorOutcome outcome = detectFacesWithFallbacks(originalFrame, normalizedFrame, brightScene);
        List<FaceDetection> filteredDetections = filterDetections(outcome.detections, originalFrame.cols(), originalFrame.rows());
        List<TrackedFace> trackedFaces = faceTracker.updateWithDetections(originalFrame, filteredDetections);
        lastDetectorLabel = outcome.engineLabel;
        lastDetectionFrame = currentFrame;
        return new DetectionCycleResult(trackedFaces, outcome.engineLabel, false);
    }

    private List<FaceDetection> filterDetections(List<FaceDetection> detections, int imgWidth, int imgHeight) {
        double minSize = imgHeight * MIN_FACE_SIZE_RATIO;
        List<FaceDetection> filtered = new ArrayList<>();
        for (FaceDetection d : detections) {
            Rect r = d.boundingBox();
            // Filter by size
            if (r.width < minSize || r.height < minSize) continue;
            
            // Filter by aspect ratio (faces are usually somewhat vertical or square)
            double ratio = r.width / (double) r.height;
            if (ratio < 0.5 || ratio > 1.6) continue;
            
            filtered.add(d);
        }
        return filtered;
    }

    private DetectionCycleResult runTrackerOnlyCycle(Mat originalFrame, Mat normalizedFrame,
                                                     long currentFrame, boolean brightScene) {
        List<TrackedFace> trackedFaces = faceTracker.track(originalFrame);
        if (trackedFaces.isEmpty()) {
            return new DetectionCycleResult(List.of(), lastDetectorLabel, true);
        }

        String suffix = brightScene && isBrightLightModeEnabled() ? " · Bright-Light" : "";
        return new DetectionCycleResult(trackedFaces, "Tracker · " + lastDetectorLabel + suffix, true);
    }

    private boolean shouldRunFreshDetection(long currentFrame, boolean brightScene) {
        if (faceTracker.isEmpty()) {
            return true;
        }
        if (currentFrame - lastDetectionFrame >= DETECTION_INTERVAL_FRAMES) {
            return true;
        }
        return brightScene && isBrightLightModeEnabled() && currentFrame - lastDetectionFrame >= 2;
    }

    private DetectorOutcome detectFacesWithFallbacks(Mat originalFrame, Mat normalizedFrame, boolean brightScene) {
        boolean preferNormalized = brightScene && isBrightLightModeEnabled();
        boolean preferYolo = yoloFaceService != null
                && yoloCooldownFramesRemaining == 0
                && (brightScene || faceTracker.averageFaceAreaRatio(originalFrame.cols(), originalFrame.rows()) < SMALL_FACE_RATIO);

        List<Mat> sources = preferNormalized ? List.of(normalizedFrame, originalFrame) : List.of(originalFrame, normalizedFrame);
        for (Mat source : sources) {
            String variantLabel = source == normalizedFrame && source != originalFrame
                    ? (brightScene ? "Bright-Light" : "Contrast")
                    : "Native";

            if (preferYolo) {
                DetectorOutcome yoloOutcome = tryYolo(source, variantLabel);
                if (!yoloOutcome.detections.isEmpty()) {
                    return yoloOutcome;
                }
            }

            DetectorOutcome ssdOutcome = trySsd(source, variantLabel);
            if (!ssdOutcome.detections.isEmpty()) {
                return ssdOutcome;
            }

            if (!preferYolo) {
                DetectorOutcome yoloOutcome = tryYolo(source, variantLabel);
                if (!yoloOutcome.detections.isEmpty()) {
                    return yoloOutcome;
                }
            }
        }

        DetectorOutcome haarOutcome = tryHaar(preferNormalized ? normalizedFrame : originalFrame, preferNormalized ? "Bright-Light" : "Native");
        if (!haarOutcome.detections.isEmpty()) {
            return haarOutcome;
        }
        if (preferNormalized) {
            DetectorOutcome secondaryHaar = tryHaar(originalFrame, "Native");
            if (!secondaryHaar.detections.isEmpty()) {
                return secondaryHaar;
            }
        }

        if (brightScene) {
            return new DetectorOutcome(List.of(), "Searching (Bright-Light)");
        }
        return new DetectorOutcome(List.of(), "Searching...");
    }

    private DetectorOutcome tryYolo(Mat frame, String variantLabel) {
        if (yoloFaceService == null || yoloCooldownFramesRemaining > 0) {
            return new DetectorOutcome(List.of(), "YOLO cooldown");
        }

        List<FaceDetection> detections = yoloFaceService.detectDetections(frame);
        if (!detections.isEmpty()) {
            yoloFailureStreak = 0;
            return new DetectorOutcome(detections, "YOLOv8" + variantSuffix(variantLabel));
        }

        yoloFailureStreak++;
        if (yoloFailureStreak >= YOLO_FAILURES_BEFORE_COOLDOWN) {
            yoloCooldownFramesRemaining = YOLO_COOLDOWN_FRAMES;
            yoloFailureStreak = 0;
            logger.info("YOLO temporarily cooled down for {} frames after repeated misses.", YOLO_COOLDOWN_FRAMES);
        }
        return new DetectorOutcome(List.of(), "YOLO miss");
    }

    private DetectorOutcome trySsd(Mat frame, String variantLabel) {
        if (faceDetectorService == null) {
            return new DetectorOutcome(List.of(), "SSD unavailable");
        }

        Rect[] faces = faceDetectorService.detectFaces(frame);
        if (faces.length == 0) {
            return new DetectorOutcome(List.of(), "SSD miss");
        }

        List<FaceDetection> detections = new ArrayList<>(faces.length);
        for (Rect face : faces) {
            detections.add(new FaceDetection(face, new Point[0], 1.0f, "SSD Caffe"));
        }
        return new DetectorOutcome(detections, "SSD Caffe" + variantSuffix(variantLabel));
    }

    private DetectorOutcome tryHaar(Mat frame, String variantLabel) {
        if (haarCascade == null || haarCascade.empty()) {
            return new DetectorOutcome(List.of(), "Haar unavailable");
        }

        org.opencv.core.MatOfRect detectionsMat = new org.opencv.core.MatOfRect();
        try {
            haarCascade.detectMultiScale(frame, detectionsMat, 1.1, 7, 0, new org.opencv.core.Size(40, 40));
            Rect[] faces = detectionsMat.toArray();
            if (faces.length == 0) {
                return new DetectorOutcome(List.of(), "Haar miss");
            }

            List<FaceDetection> detections = new ArrayList<>(faces.length);
            for (Rect face : faces) {
                detections.add(new FaceDetection(face, new Point[0], 0.75f, "Haar"));
            }
            return new DetectorOutcome(detections, "Haar" + variantSuffix(variantLabel));
        } finally {
            detectionsMat.release();
        }
    }

    private String variantSuffix(String variantLabel) {
        return "Native".equals(variantLabel) ? "" : " · " + variantLabel;
    }

    private void drawFaceAnnotations(Mat outputFrame, Mat analysisFrame, List<TrackedFace> faces, long currentFrame) {
        Set<Integer> activeIds = new HashSet<>();
        for (TrackedFace face : faces) {
            activeIds.add(face.id());
            Rect rect = face.boundingBox();
            org.opencv.imgproc.Imgproc.rectangle(outputFrame, rect.tl(), rect.br(), new org.opencv.core.Scalar(0, 255, 0), 2);

            long lastPrediction = lastGenderPredictionFrame.getOrDefault(face.id(), Long.MIN_VALUE / 4);
            Rect lastBox = lastGenderPredictionBox.get(face.id());
            boolean boxChangedSignificantly = lastBox == null || computeBoxChange(lastBox, rect) > BOX_CHANGE_THRESHOLD;

            boolean shouldPredict = !genderCache.containsKey(face.id())
                    || (currentFrame - lastPrediction >= GENDER_PREDICTION_INTERVAL && boxChangedSignificantly);

            if (faceDetectorService != null && shouldPredict) {
                String[] result = faceDetectorService.predictGender(analysisFrame, rect, face.landmarksCopy());
                genderCache.put(face.id(), result);
                lastGenderPredictionFrame.put(face.id(), currentFrame);
                lastGenderPredictionBox.put(face.id(), new Rect(rect.x, rect.y, rect.width, rect.height));
            }

            String[] genderInfo = genderCache.get(face.id());
            if (genderInfo != null) {
                String label = genderInfo[0] + " " + genderInfo[1];
                org.opencv.imgproc.Imgproc.putText(outputFrame, label,
                        new org.opencv.core.Point(rect.x, rect.y - 10),
                        org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                        new org.opencv.core.Scalar(0, 255, 255), 2);
            }
        }

        genderCache.keySet().retainAll(activeIds);
        lastGenderPredictionFrame.keySet().retainAll(activeIds);
        lastGenderPredictionBox.keySet().retainAll(activeIds);
    }

    private double computeBoxChange(Rect oldBox, Rect newBox) {
        double dw = Math.abs(oldBox.width - newBox.width) / (double) oldBox.width;
        double dh = Math.abs(oldBox.height - newBox.height) / (double) oldBox.height;
        double dx = Math.abs(oldBox.x - newBox.x) / (double) oldBox.width;
        double dy = Math.abs(oldBox.y - newBox.y) / (double) oldBox.height;
        return (dw + dh + dx + dy) / 4.0;
    }

    private void stopCamera() {
        releaseCamera();
        cameraButton.setText("\u23FA  Start Camera");
        cameraButton.getStyleClass().remove("btn-danger");
        cameraButton.getStyleClass().add("btn-primary");
        sourceCard.setVisible(true);
        sourceCard.setManaged(true);
    }

    private void releaseCamera() {
        cameraActive = false;

        if (cameraTask != null) {
            cameraTask.cancel(true);
            cameraTask = null;
        }

        if (timer != null && !timer.isShutdown()) {
            timer.shutdownNow();
            try {
                timer.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            timer = null;
        }

        faceTracker.reset();
        genderCache.clear();
        lastGenderPredictionFrame.clear();

        try {
            if (capture != null && capture.isOpened()) {
                capture.release();
            }
        } catch (Exception e) {
            logger.error("Error releasing camera", e);
        }
    }

    private Mat grabFrame() {
        Mat frame = new Mat();
        if (capture != null && capture.isOpened()) {
            try {
                capture.read(frame);
            } catch (Exception e) {
                logger.error("Exception during frame grabbing", e);
            }
        }
        return frame;
    }

    private Mat prepareDisplayFrame(Mat frame) {
        Mat prepared = new Mat();
        if (frame.cols() > 640) {
            double scale = 640.0 / frame.cols();
            org.opencv.imgproc.Imgproc.resize(frame, prepared, new org.opencv.core.Size(640, frame.rows() * scale));
        } else {
            frame.copyTo(prepared);
        }

        if (prepared.channels() == 4) {
            Mat bgr = new Mat();
            org.opencv.imgproc.Imgproc.cvtColor(prepared, bgr, org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR);
            safeRelease(prepared);
            return bgr;
        }
        if (prepared.channels() == 1) {
            Mat bgr = new Mat();
            org.opencv.imgproc.Imgproc.cvtColor(prepared, bgr, org.opencv.imgproc.Imgproc.COLOR_GRAY2BGR);
            safeRelease(prepared);
            return bgr;
        }
        return prepared;
    }

    private boolean shouldRetuneExposure(long currentFrame, Mat currentFrameMat) {
        if (!cameraActive || !isAdaptiveExposureEnabled()) {
            return false;
        }
        if (currentFrame - lastExposureTuneFrame < EXPOSURE_RETUNE_INTERVAL_FRAMES) {
            return false;
        }

        double currentBrightness = VisionPreprocessor.estimateBrightness(currentFrameMat);
        if (currentBrightness > 235.0) return true; // Definitely overexposed

        // If brightness is very different from ideal, retune
        return Math.abs(currentBrightness - CAMERA_TARGET_BRIGHTNESS) > 40.0;
    }

    private void optimizeCameraExposureIfSupported(long currentFrame, Mat seedFrame) {
        if (capture == null || !capture.isOpened()) {
            return;
        }

        double baselineBrightness = seedFrame != null && !seedFrame.empty()
                ? VisionPreprocessor.estimateBrightness(seedFrame)
                : sampleCameraBrightness(3);
        lastExposureTuneFrame = currentFrame;

        if (baselineBrightness <= 0.0 || baselineBrightness <= CAMERA_TARGET_BRIGHTNESS) {
            exposureStatusText = String.format("Exposure: Stable %.0f", baselineBrightness);
            return;
        }

        boolean manualModeRequested = tryEnableManualExposureMode();
        double originalExposure = capture.get(Videoio.CAP_PROP_EXPOSURE);
        double bestExposure = originalExposure;
        double bestBrightness = baselineBrightness;
        boolean foundImprovement = false;

        for (double candidateExposure : buildExposureCandidates(originalExposure)) {
            if (!capture.set(Videoio.CAP_PROP_EXPOSURE, candidateExposure)) {
                continue;
            }

            sleepQuietly(CAMERA_EXPOSURE_SETTLE_MS);
            double candidateBrightness = sampleCameraBrightness(2);
            if (candidateBrightness >= CAMERA_MIN_BRIGHTNESS && candidateBrightness < bestBrightness) {
                bestBrightness = candidateBrightness;
                bestExposure = candidateExposure;
                foundImprovement = true;
            }

            if (candidateBrightness <= CAMERA_TARGET_BRIGHTNESS
                    && baselineBrightness - candidateBrightness >= CAMERA_MEANINGFUL_DROP) {
                break;
            }
        }

        if (foundImprovement) {
            capture.set(Videoio.CAP_PROP_EXPOSURE, bestExposure);
            sleepQuietly(CAMERA_EXPOSURE_SETTLE_MS);
            
            // Try to tune gain as well
            retuneGainAndWhiteBalanceIfSupported(bestBrightness);
            
            exposureStatusText = String.format("Exposure: Tuned %.0f -> %.0f", baselineBrightness, bestBrightness);
            logger.info("Applied camera exposure adjustment: {} -> {} (brightness {} -> {}, manualModeRequested={})",
                    originalExposure, bestExposure,
                    String.format("%.1f", baselineBrightness),
                    String.format("%.1f", bestBrightness),
                    manualModeRequested);
            return;
        }

        if (Double.isFinite(originalExposure)) {
            capture.set(Videoio.CAP_PROP_EXPOSURE, originalExposure);
        }
        exposureStatusText = manualModeRequested ? "Exposure: Driver locked" : "Exposure: Manual unsupported";
    }

    private void retuneGainAndWhiteBalanceIfSupported(double currentBrightness) {
        if (capture == null || !capture.isOpened()) return;

        try {
            // If still too bright, try lowering gain
            if (currentBrightness > CAMERA_TARGET_BRIGHTNESS + 20) {
                double gain = capture.get(Videoio.CAP_PROP_GAIN);
                if (gain > 0) {
                    capture.set(Videoio.CAP_PROP_GAIN, Math.max(0, gain - 1));
                }
            }
            
            // Re-enable auto white balance for a moment to let it adjust
            capture.set(Videoio.CAP_PROP_AUTO_WB, 1.0);
            sleepQuietly(50);
        } catch (Exception e) {
            logger.debug("Gain/WB tuning not supported: {}", e.getMessage());
        }
    }

    private boolean tryEnableManualExposureMode() {
        boolean changed = false;
        for (double mode : MANUAL_EXPOSURE_MODES) {
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

    private void tryEnableAutomaticExposureMode() {
        if (capture == null || !capture.isOpened()) {
            return;
        }

        for (double mode : AUTOMATIC_EXPOSURE_MODES) {
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

    private double sampleCameraBrightness(int framesToSample) {
        double total = 0.0;
        int validFrames = 0;
        for (int i = 0; i < framesToSample; i++) {
            Mat sample = grabFrame();
            try {
                if (sample == null || sample.empty()) {
                    continue;
                }
                total += VisionPreprocessor.estimateBrightness(sample);
                validFrames++;
            } finally {
                safeRelease(sample);
            }
        }
        return validFrames == 0 ? 0.0 : total / validFrames;
    }

    private void processSelectedImage(File file) {
        Mat image = Imgcodecs.imread(file.getAbsolutePath());
        if (image.empty()) {
            showError("Image Error", "Cannot read image at " + file.getAbsolutePath());
            return;
        }

        Mat processedImage = null;
        Mat normalizedImage = null;
        try {
            sourceCard.setVisible(true);
            sourceCard.setManaged(true);

            originalImageView.setImage(mat2Image(image));
            processedImage = image.clone();
            normalizedImage = isBrightLightModeEnabled()
                    ? VisionPreprocessor.prepareForFaceDetection(image)
                    : image.clone();

            faceTracker.reset();
            DetectorOutcome outcome = detectFacesWithFallbacks(image, normalizedImage, VisionPreprocessor.isLikelyOverexposed(image));
            List<TrackedFace> trackedFaces = faceTracker.updateWithDetections(image, outcome.detections);
            drawFaceAnnotations(processedImage, normalizedImage, trackedFaces, frameCounter.incrementAndGet());

            processedImageView.setImage(mat2Image(processedImage));
            if (!updateFound) {
                statusLabel.setText("Done \u00B7 " + trackedFaces.size() + " face" +
                        (trackedFaces.size() != 1 ? "s" : "") + " detected");
            }
            if (engineLabel != null) {
                engineLabel.setText("Engine: " + outcome.engineLabel);
            }
            if (fpsLabel != null) {
                fpsLabel.setText("FPS: Static");
            }
            if (exposureStatusLabel != null) {
                exposureStatusLabel.setText("Exposure: N/A for image");
            }
        } finally {
            safeRelease(processedImage);
            safeRelease(normalizedImage);
            safeRelease(image);
        }
    }

    private Image mat2Image(Mat frame) {
        if (frame == null || frame.empty()) {
            return null;
        }

        try {
            synchronized (encodingBuffer) {
                Imgcodecs.imencode(".bmp", frame, encodingBuffer);
                return new Image(new ByteArrayInputStream(encodingBuffer.toArray()));
            }
        } catch (Exception e) {
            logger.error("Error converting Mat to Image", e);
            return null;
        }
    }

    private void bindImageViewToParent(ImageView imageView, StackPane parent) {
        if (imageView == null || parent == null) {
            return;
        }
        imageView.fitWidthProperty().bind(parent.widthProperty().subtract(16));
        imageView.fitHeightProperty().bind(parent.heightProperty().subtract(16));
    }

    private static void safeRelease(Mat mat) {
        if (mat != null) {
            try {
                mat.release();
            } catch (Exception ignored) {
                // Ignore already released mats.
            }
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void detectCameras() {
        List<String> cameras = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            VideoCapture temp = new VideoCapture(i);
            if (temp.isOpened()) {
                cameras.add("Camera " + i);
                temp.release();
            }
        }

        if (cameras.isEmpty()) {
            cameras.add("No Camera Found");
        }
        cameraSelector.getItems().setAll(cameras);
        cameraSelector.getSelectionModel().selectFirst();
        logger.info("Detected {} camera(s)", cameras.size());
    }

    private void checkUpdates() {
        CompletableFuture.runAsync(() -> {
            try {
                Instant buildInstant = getLocalBuildTime();
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/OniAntou/Face-Recognition/releases/latest"))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }

                String body = response.body();
                java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("\"published_at\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"").matcher(body);
                String downloadUrl = urlMatcher.find() ? urlMatcher.group(1) : null;
                if (dateMatcher.find() && downloadUrl != null) {
                    Instant latestReleaseInstant = Instant.parse(dateMatcher.group(1));
                    if (latestReleaseInstant.isAfter(buildInstant.plus(Duration.ofHours(1)))) {
                        updateFound = true;
                        Platform.runLater(() -> {
                            statusLabel.setText("New Update Found (Click to install)");
                            statusLabel.setStyle("-fx-text-fill: #FFCC00; -fx-cursor: hand;");
                            statusLabel.setOnMouseClicked(event -> downloadAndInstall(downloadUrl));
                        });
                    }
                }
            } catch (Exception e) {
                logger.warn("Update check failed: {}", e.getMessage());
            }
        });
    }

    private Instant getLocalBuildTime() {
        try {
            URL res = ViewController.class.getProtectionDomain().getCodeSource().getLocation();
            if (res != null) {
                URL manifestUrl = new URL("jar:" + res.toExternalForm() + "!/META-INF/MANIFEST.MF");
                try (java.io.InputStream is = manifestUrl.openStream()) {
                    Manifest manifest = new Manifest(is);
                    String buildTime = manifest.getMainAttributes().getValue("Build-Time");
                    if (buildTime != null) {
                        return Instant.parse(buildTime);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read Build-Time from manifest: {}", e.getMessage());
        }
        return Instant.now().minus(Duration.ofDays(365));
    }

    private void downloadAndInstall(String downloadUrl) {
        statusLabel.setText("Downloading update... please wait");
        statusLabel.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .build();

                File tempFile = File.createTempFile("FaceRecognition_Setup_", ".exe");
                tempFile.deleteOnExit();
                client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()));

                logger.info("Update downloaded to {}", tempFile.getAbsolutePath());
                new ProcessBuilder(tempFile.getAbsolutePath(), "/SILENT", "/SP-", "/NOCANCEL").start();
                System.exit(0);
            } catch (Exception e) {
                logger.error("Failed to download update", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Update failed. Try again later.");
                    statusLabel.setDisable(false);
                });
            }
        });
    }

    public void shutdown() {
        releaseCamera();
        aiExecutor.shutdownNow();
        faceTracker.close();
        if (yoloFaceService != null) {
            yoloFaceService.close();
        }
        synchronized (encodingBuffer) {
            try {
                encodingBuffer.release();
            } catch (Exception ignored) {
                // Ignore.
            }
        }
    }

    private double updateFps() {
        long now = System.nanoTime();
        long previous = lastFpsTimestampNanos.getAndSet(now);
        if (previous == 0L) {
            return smoothedFps;
        }

        double instantaneous = 1_000_000_000.0 / Math.max(1L, now - previous);
        smoothedFps = smoothedFps == 0.0 ? instantaneous : smoothedFps * 0.80 + instantaneous * 0.20;
        return smoothedFps;
    }

    private boolean isAdaptiveExposureEnabled() {
        return adaptiveExposureCheckBox == null || adaptiveExposureCheckBox.isSelected();
    }

    private boolean isBrightLightModeEnabled() {
        return brightLightModeCheckBox == null || brightLightModeCheckBox.isSelected();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record FrameRenderResult(Image originalImage, Image processedImage, String statusText, String engineText, double fps) {
    }
}
