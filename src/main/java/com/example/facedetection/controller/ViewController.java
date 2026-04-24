package com.example.facedetection.controller;

import com.example.facedetection.MainApp;
import com.example.facedetection.service.FaceDetectorService;
import com.example.facedetection.service.YoloFaceService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * JavaFX controller for the Face Recognition UI.
 * <p>
 * Optimizations applied:
 * <ul>
 *   <li>Atomic flag to prevent concurrent frame processing</li>
 *   <li>Daemon thread for camera executor (prevents JVM hang on exit)</li>
 *   <li>Reusable {@link MatOfByte} buffer for Mat-to-Image conversion</li>
 *   <li>Proper camera resource cleanup with timeout</li>
 *   <li>Frame skipping when processing can't keep up with capture rate</li>
 *   <li>Camera resolution configuration for optimal performance</li>
 *   <li>Separated resource cleanup from UI updates for thread-safe shutdown</li>
 * </ul>
 */
public class ViewController {

    private static final Logger logger = LoggerFactory.getLogger(ViewController.class);

    /** Target camera frame interval in milliseconds (~30 FPS). */
    private static final long FRAME_INTERVAL_MS = 33;

    /** Maximum time to wait for the camera thread to shut down (ms). */
    private static final long SHUTDOWN_TIMEOUT_MS = 500;

    @FXML private ImageView originalImageView;
    @FXML private ImageView processedImageView;
    @FXML private StackPane sourceViewport;
    @FXML private StackPane resultViewport;
    @FXML private VBox sourceCard;
    @FXML private Button cameraButton;
    @FXML private ComboBox<String> cameraSelector;
    @FXML private Label statusLabel;

    private FaceDetectorService faceDetectorService;
    private YoloFaceService yoloFaceService;
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> cameraTask;
    private volatile boolean cameraActive = false;
    private volatile boolean updateFound = false;

    /** Guard flag to skip frames if previous frame is still being processed. */
    private final AtomicBoolean processing = new AtomicBoolean(false);

    /** Dedicated executor for AI processing to keep capture and UI threads responsive. */
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-processor");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
        return t;
    });

    /** Counter for throttling heavy AI operations (like gender prediction). */
    private final AtomicLong frameCounter = new AtomicLong(0);
    private static final int GENDER_PREDICTION_INTERVAL = 10; // Predict every 10 frames
    private int lastFaceCount = 0;

    /** Cache for detected faces to reduce flickering and redundant AI calls. */
    private final Map<Integer, String[]> genderCache = new ConcurrentHashMap<>();

    /** Reusable buffer for Mat -> Image encoding (reduces GC pressure). */
    private final MatOfByte encodingBuffer = new MatOfByte();

    public void initialize() {
        try {
            // Smart path resolution: check current directory first, then fallback to 'app' directory (for jpackage)
            String faceModelFile   = "res10_300x300_ssd_iter_140000.caffemodel";
            String faceConfigFile  = "deploy.prototxt";
            String genderModelFile = "gender_net.caffemodel";
            String genderConfigFile = "gender_deploy.prototxt";
            String yoloModelFile   = "yolov8n-face.onnx";

            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir = new File("app" + File.separator + "data");
            }

            File yoloFile = new File(dataDir, yoloModelFile);
            if (yoloFile.exists()) {
                logger.info("YOLO model detected, using YOLO for detection.");
                yoloFaceService = new YoloFaceService(yoloFile.getAbsolutePath());
            }

            faceDetectorService = new FaceDetectorService(
                    new File(dataDir, faceModelFile).getAbsolutePath(),
                    new File(dataDir, faceConfigFile).getAbsolutePath(),
                    new File(dataDir, genderModelFile).getAbsolutePath(),
                    new File(dataDir, genderConfigFile).getAbsolutePath());
            capture = new VideoCapture();

            // Bind ImageViews to fill their parent containers dynamically
            bindImageViewToParent(originalImageView, sourceViewport);
            bindImageViewToParent(processedImageView, resultViewport);

            // Detect available cameras
            detectCameras();

            // Check for updates in background
            checkUpdates();
        } catch (Exception e) {
            logger.error("Initialization Error", e);
            showError("Initialization Error", "Could not load AI models: " + e.getMessage());
        }
    }

    // ── Image Selection ─────────────────────────────────────────────────────

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

    // ── Camera Controls ─────────────────────────────────────────────────────

    @FXML
    private void toggleCamera() {
        if (!cameraActive) {
            startCamera();
        } else {
            stopCamera();
        }
    }

    private void startCamera() {
        int cameraIndex = 0;
        String selected = cameraSelector.getSelectionModel().getSelectedItem();
        if (selected != null && selected.contains("Camera ")) {
            try {
                cameraIndex = Integer.parseInt(selected.replace("Camera ", ""));
            } catch (NumberFormatException ignored) {}
        }

        capture.open(cameraIndex);
        if (!capture.isOpened()) {
            showError("Camera Error", "Could not open camera device at index " + cameraIndex);
            return;
        }

        // Configure camera for optimal performance
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
        capture.set(Videoio.CAP_PROP_BUFFERSIZE, 1);

        cameraActive = true;
        cameraButton.setText("\u23F9  Stop Camera");
        cameraButton.getStyleClass().remove("btn-primary");
        cameraButton.getStyleClass().add("btn-danger");

        // Hide source view for camera feed
        sourceCard.setVisible(false);
        sourceCard.setManaged(false);

        // Create daemon thread executor (won't block JVM exit)
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camera-capture");
            t.setDaemon(true);
            return t;
        });

        cameraTask = timer.scheduleAtFixedRate(this::captureAndProcessFrame,
                0, FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Captures a single frame from the camera and processes it.
     * Uses an atomic flag to skip frames if the previous one hasn't finished yet.
     */
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

        // Offload AI processing to a DEDICATED background task
        CompletableFuture.supplyAsync(() -> {
            Mat processedFrame = new Mat();
            try {
                // Downscale to 480p width if original is larger, for faster processing
                double scale = 1.0;
                if (frame.cols() > 640) {
                    scale = 640.0 / frame.cols();
                    org.opencv.imgproc.Imgproc.resize(frame, processedFrame, new org.opencv.core.Size(640, frame.rows() * scale));
                } else {
                    frame.copyTo(processedFrame);
                }
                
                // Use YOLO if available, otherwise fallback to SSD Caffe
                Rect[] faces;
                int faceCount;
                
                if (yoloFaceService != null) {
                    faces = yoloFaceService.detectFaces(processedFrame);
                } else if (faceDetectorService != null) {
                    faces = faceDetectorService.detectFaces(processedFrame);
                } else {
                    faces = new Rect[0];
                }
                
                faceCount = faces.length;
                
                // If number of faces changed, clear cache to avoid label swapping
                if (faceCount != lastFaceCount) {
                    genderCache.clear();
                    lastFaceCount = faceCount;
                }

                for (int i = 0; i < faces.length; i++) {
                    Rect rect = faces[i];
                    org.opencv.imgproc.Imgproc.rectangle(processedFrame, rect.tl(), rect.br(), new org.opencv.core.Scalar(0, 255, 0), 2);
                    
                    // Throttling: Only predict gender every X frames OR if not in cache
                    if (faceDetectorService != null && (currentFrame % GENDER_PREDICTION_INTERVAL == 0 || !genderCache.containsKey(i))) {
                        String[] result = faceDetectorService.predictGender(processedFrame, rect);
                        genderCache.put(i, result);
                    }
                    
                    String[] genderInfo = genderCache.get(i);
                    if (genderInfo != null) {
                        String label = genderInfo[0] + " " + genderInfo[1];
                        org.opencv.imgproc.Imgproc.putText(processedFrame, label, 
                            new org.opencv.core.Point(rect.x, rect.y - 10),
                            org.opencv.imgproc.Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, 
                            new org.opencv.core.Scalar(0, 255, 255), 2);
                    }
                }
                
                // Encode both for UI - Note: mat2Image now faster
                Image original = mat2Image(frame);
                Image processed = mat2Image(processedFrame);
                
                return new Object[]{original, processed, faceCount};
            } finally {
                safeRelease(processedFrame);
            }
        }, aiExecutor).thenAcceptAsync(results -> {
            if (!cameraActive) return;
            
            originalImageView.setImage((Image) results[0]);
            processedImageView.setImage((Image) results[1]);
            
            // Only update status label if no update notification is active
            if (!updateFound) {
                int count = (int) results[2];
                statusLabel.setText("Live \u00B7 " + count + " face" + (count != 1 ? "s" : "") + " detected");
            }
        }, Platform::runLater).whenComplete((v, e) -> {
            safeRelease(frame);
            processing.set(false);
        });
    }

    /**
     * Stops the camera and updates UI. Call from JavaFX Application Thread only.
     */
    private void stopCamera() {
        // Release all native resources first
        releaseCamera();

        // Update UI (safe because this is called from JavaFX thread)
        cameraButton.setText("\u23FA  Start Camera");
        cameraButton.getStyleClass().remove("btn-danger");
        cameraButton.getStyleClass().add("btn-primary");

        // Show source panel again
        sourceCard.setVisible(true);
        sourceCard.setManaged(true);
    }

    /**
     * Releases all camera and timer resources WITHOUT touching UI.
     * Safe to call from ANY thread (including shutdown hooks and background threads).
     */
    private void releaseCamera() {
        cameraActive = false;

        // Cancel the scheduled task first
        if (cameraTask != null) {
            cameraTask.cancel(true);
            cameraTask = null;
        }

        // Shut down executor — use shutdownNow() for fastest cleanup
        if (timer != null && !timer.isShutdown()) {
            timer.shutdownNow();
            try {
                timer.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            timer = null;
        }

        // We don't shut down aiExecutor here because it's used globally, 
        // but we should clear the cache
        genderCache.clear();

        // Release camera device
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

    // ── Image Processing ────────────────────────────────────────────────────

    private void processSelectedImage(File file) {
        Mat image = Imgcodecs.imread(file.getAbsolutePath());
        if (image.empty()) {
            showError("Image Error", "Cannot read image at " + file.getAbsolutePath());
            return;
        }

        Mat processedImage = null;
        try {
            // Show source view for images
            sourceCard.setVisible(true);
            sourceCard.setManaged(true);

            originalImageView.setImage(mat2Image(image));

            processedImage = image.clone();
            int faceCount = faceDetectorService.detectAndDrawFaces(processedImage);
            processedImageView.setImage(mat2Image(processedImage));
            if (!updateFound) {
                statusLabel.setText("Done \u00B7 " + faceCount + " face" + (faceCount != 1 ? "s" : "") + " detected");
            }
        } finally {
            safeRelease(processedImage);
            safeRelease(image);
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    /**
     * Converts an OpenCV Mat to a JavaFX Image using BMP encoding for speed.
     * Reuses a single MatOfByte buffer to reduce native memory allocation churn.
     */
    private Image mat2Image(Mat frame) {
        if (frame == null || frame.empty()) return null;

        try {
            // Using .bmp is much faster than .png for real-time conversion
            synchronized (encodingBuffer) {
                Imgcodecs.imencode(".bmp", frame, encodingBuffer);
                return new Image(new ByteArrayInputStream(encodingBuffer.toArray()));
            }
        } catch (Exception e) {
            logger.error("Error converting Mat to Image", e);
            return null;
        }
    }

    /**
     * Binds an ImageView's fit dimensions to its parent StackPane so that
     * the image automatically scales to fill all available space.
     */
    private void bindImageViewToParent(ImageView imageView, StackPane parent) {
        if (imageView == null || parent == null) return;
        // Subtract padding so the image doesn't overflow the card borders
        imageView.fitWidthProperty().bind(parent.widthProperty().subtract(16));
        imageView.fitHeightProperty().bind(parent.heightProperty().subtract(16));
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Detects available camera devices by attempting to open indices.
     */
    private void detectCameras() {
        List<String> cameras = new ArrayList<>();
        // Check first 5 indices as a reasonable limit
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

    /**
     * Checks for application updates from GitHub API by comparing Build-Time with published_at.
     */
    private void checkUpdates() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Get local build time from Manifest
                Instant buildInstant = getLocalBuildTime();
                logger.info("Local Build Time: {}", buildInstant);

                // Step 2: Fetch latest release info from GitHub
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/OniAntou/Face-Recognition/releases/latest"))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    
                    // Find published_at (e.g. "published_at":"2024-04-24T12:00:00Z")
                    java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("\"published_at\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                    
                    // Find the download URL for the .exe asset
                    java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"").matcher(body);
                    String downloadUrl = urlMatcher.find() ? urlMatcher.group(1) : null;
                    
                    if (dateMatcher.find() && downloadUrl != null) {
                        Instant latestReleaseInstant = Instant.parse(dateMatcher.group(1));
                        logger.info("Latest Release Time: {}", latestReleaseInstant);

                        // If release is newer than build time (plus 1 hour buffer to account for build-to-publish lag)
                        if (latestReleaseInstant.isAfter(buildInstant.plus(Duration.ofHours(1)))) {
                            updateFound = true;
                            Platform.runLater(() -> {
                                statusLabel.setText("New Update Found (Click to install)");
                                statusLabel.setStyle("-fx-text-fill: #FFCC00; -fx-cursor: hand;");
                                statusLabel.setOnMouseClicked(event -> downloadAndInstall(downloadUrl));
                            });
                        } else {
                            logger.info("Application is up to date (Latest: {}, Local: {})", latestReleaseInstant, buildInstant);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Update check failed: {}", e.getMessage());
            }
        });
    }

    private Instant getLocalBuildTime() {
        try {
            // Get the manifest of the JAR containing this class
            URL res = ViewController.class.getProtectionDomain().getCodeSource().getLocation();
            if (res != null) {
                // If it's a jar file, open the manifest inside it
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
        // Fallback for development: use current time minus a bit so we don't spam updates during dev
        // but still allow testing the UI.
        return Instant.now().minus(Duration.ofDays(365));
    }
    /**
     * Downloads the update installer in background and launches it.
     */
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
                
                // Launch the installer with robust flags
                // /SILENT: show progress only
                // /SP-: skip the "This will install..." prompt
                // /NOCANCEL: prevent accidental cancellation
                new ProcessBuilder(tempFile.getAbsolutePath(), "/SILENT", "/SP-", "/NOCANCEL").start();
                
                // Exit application immediately so installer can overwrite files
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

    /**
     * Called when the application is shutting down. Ensures camera resources are freed.
     * Safe to call from any thread — only touches native resources, not UI.
     */
    public void shutdown() {
        releaseCamera();
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        synchronized (encodingBuffer) {
            try {
                encodingBuffer.release();
            } catch (Exception ignored) {
                // Already released
            }
        }
    }
}