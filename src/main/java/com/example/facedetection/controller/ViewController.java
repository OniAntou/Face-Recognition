package com.example.facedetection.controller;

import com.example.facedetection.service.FaceDetectorService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    @FXML private Label statusLabel;

    private FaceDetectorService faceDetectorService;
    private VideoCapture capture;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> cameraTask;
    private volatile boolean cameraActive = false;

    /** Guard flag to skip frames if previous frame is still being processed. */
    private final AtomicBoolean processing = new AtomicBoolean(false);

    /** Reusable buffer for Mat -> Image encoding (reduces GC pressure). */
    private final MatOfByte encodingBuffer = new MatOfByte();

    public void initialize() {
        try {
            // Smart path resolution: check current directory first, then fallback to 'app' directory (for jpackage)
            String faceModelFile   = "res10_300x300_ssd_iter_140000.caffemodel";
            String faceConfigFile  = "deploy.prototxt";
            String genderModelFile = "gender_net.caffemodel";
            String genderConfigFile = "gender_deploy.prototxt";

            File dataDir = new File("data");
            if (!dataDir.exists()) {
                // Fallback for jpackage installation structure
                dataDir = new File("app" + File.separator + "data");
            }

            String faceModelPath   = new File(dataDir, faceModelFile).getAbsolutePath();
            String faceConfigPath  = new File(dataDir, faceConfigFile).getAbsolutePath();
            String genderModelPath = new File(dataDir, genderModelFile).getAbsolutePath();
            String genderConfigPath = new File(dataDir, genderConfigFile).getAbsolutePath();

            faceDetectorService = new FaceDetectorService(
                    faceModelPath, faceConfigPath, genderModelPath, genderConfigPath);
            capture = new VideoCapture();

            // Bind ImageViews to fill their parent containers dynamically
            bindImageViewToParent(originalImageView, sourceViewport);
            bindImageViewToParent(processedImageView, resultViewport);
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
        capture.open(0);
        if (!capture.isOpened()) {
            showError("Camera Error", "Could not open camera device.");
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
        // Skip this frame if we're still processing the previous one
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        Mat frame = null;
        Mat processedFrame = null;
        try {
            frame = grabFrame();
            if (frame == null || frame.empty()) {
                return;
            }

            Image original = mat2Image(frame);

            // Process a copy for detection
            processedFrame = frame.clone();
            int faceCount = faceDetectorService.detectAndDrawFaces(processedFrame);
            Image processed = mat2Image(processedFrame);

            // Update UI on JavaFX thread
            final int count = faceCount;
            Platform.runLater(() -> {
                if (!cameraActive) return; // Guard against late updates after stop
                originalImageView.setImage(original);
                processedImageView.setImage(processed);
                statusLabel.setText("Live \u00B7 " + count + " face" + (count != 1 ? "s" : "") + " detected");
            });
        } catch (Exception e) {
            logger.error("Error processing camera frame", e);
        } finally {
            safeRelease(processedFrame);
            safeRelease(frame);
            processing.set(false);
        }
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
            statusLabel.setText("Done \u00B7 " + faceCount + " face" + (faceCount != 1 ? "s" : "") + " detected");
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
     * Called when the application is shutting down. Ensures camera resources are freed.
     * Safe to call from any thread — only touches native resources, not UI.
     */
    public void shutdown() {
        releaseCamera();
        synchronized (encodingBuffer) {
            try {
                encodingBuffer.release();
            } catch (Exception ignored) {
                // Already released
            }
        }
    }
}