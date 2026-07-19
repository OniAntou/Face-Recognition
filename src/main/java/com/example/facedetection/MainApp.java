package com.example.facedetection;

import atlantafx.base.theme.PrimerDark;
import com.example.facedetection.controller.ViewController;
import com.example.facedetection.service.PreferencesService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class MainApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static final double DEFAULT_WIDTH = 1120;
    private static final double DEFAULT_HEIGHT = 720;

    private static javafx.application.HostServices hostServicesInstance;
    private ViewController controller;
    private PreferencesService preferencesService;
    private final java.util.concurrent.atomic.AtomicBoolean isShuttingDown = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void init() {
        // Resource cleanup is coordinated by stop()/performShutdown(). Avoid
        // Runtime.halt(), which can terminate native cleanup mid-operation.
    }

    @Override
    public void start(Stage stage) {
        try {
            // Initialize preferences
            preferencesService = new PreferencesService();

            // Set theme
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

            // Store HostServices for later use
            hostServicesInstance = getHostServices();

            // Resolve the bundled native library consistently in development
            // and packaged launches.
            OpenCV.loadLocally();

            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("scene.fxml"));
            Parent root = fxmlLoader.load();

            // Keep a reference to the controller for graceful shutdown
            controller = fxmlLoader.getController();

            Scene scene = new Scene(root);
            stage.setTitle("Face Recognition");
            stage.setMinWidth(800);
            stage.setMinHeight(500);

            // Restore window size from preferences
            Optional<double[]> savedSize = preferencesService.getWindowSize();
            if (savedSize.isPresent()) {
                double[] size = savedSize.get();
                stage.setWidth(size[0]);
                stage.setHeight(size[1]);
            } else {
                stage.setWidth(DEFAULT_WIDTH);
                stage.setHeight(DEFAULT_HEIGHT);
            }

            // Restore maximized state
            if (preferencesService.isWindowMaximized(false)) {
                stage.setMaximized(true);
            }

            stage.setScene(scene);

            // Save window state on close
            stage.setOnCloseRequest(event -> {
                event.consume();
                saveWindowState(stage);
                stage.hide();
                performShutdown();
            });

            // Window dimensions are persisted once on close. Persisting from
            // every resize event performs a synchronous Preferences.flush().
            stage.maximizedProperty().addListener((obs, oldVal, newVal) -> {
                preferencesService.setWindowMaximized(newVal);
            });

            stage.show();
            logger.info("Application started successfully.");
        } catch (Throwable e) {
            logger.error("Fatal startup error", e);
            showFatalError(e);
        }
    }

    private void saveWindowState(Stage stage) {
        if (preferencesService != null && !stage.isMaximized()) {
            preferencesService.setWindowSize(stage.getWidth(), stage.getHeight());
        }
    }

    /**
     * Performs graceful shutdown: releases resources, then kills the JVM.
     * Runs the resource cleanup on a background thread to avoid blocking
     * the JavaFX thread if OpenCV hangs.
     */
    private void performShutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }

        logger.info("Application shutting down...");

        // Start a daemon thread to do the cleanup
        Thread shutdownThread = new Thread(() -> {
            try {
                if (controller != null) {
                    controller.shutdown();
                }
            } catch (Throwable e) {
                logger.error("Error during controller shutdown", e);
            } finally {
                // Force exit. This will trigger the shutdown hook (safety net)
                System.exit(0);
            }
        }, "app-shutdown");

        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    private void showFatalError(Throwable e) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Startup Error");
        alert.setHeaderText("The application failed to start");
        alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
        alert.showAndWait();
        System.exit(1);
    }

    @Override
    public void stop() {
        // Ensure we try to shut down even if called by Platform.exit()
        performShutdown();
    }

    public static javafx.application.HostServices getHostServicesInstance() {
        return hostServicesInstance;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
