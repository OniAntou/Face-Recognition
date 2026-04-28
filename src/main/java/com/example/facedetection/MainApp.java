package com.example.facedetection;

import atlantafx.base.theme.PrimerDark;
import com.example.facedetection.controller.ViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static javafx.application.HostServices hostServicesInstance;
    private ViewController controller;
    private final java.util.concurrent.atomic.AtomicBoolean isShuttingDown = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void init() {
        // Register a safety-net shutdown hook that will forcibly kill the JVM
        // if the normal stop() flow hangs (e.g. OpenCV native thread stuck).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // If we're still here after 2 seconds of shutdown start, force halt.
                // This is a safety net for native OpenCV threads that don't respect interrupts.
                Thread.sleep(2000);
                logger.info("Shutdown safety net triggered: Forcing halt...");
                Runtime.getRuntime().halt(0);
            } catch (InterruptedException ignored) {}
        }, "shutdown-safety-net"));
    }

    @Override
    public void start(Stage stage) {
        try {
            // Set theme
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            
            // Store HostServices for later use
            hostServicesInstance = getHostServices();

            // Try loading OpenCV
            try {
                OpenCV.loadShared();
            } catch (Throwable e) {
                logger.warn("OpenCV.loadShared() failed, trying loadLocally()", e);
                OpenCV.loadLocally();
            }

            FXMLLoader fxmlLoader = new FXMLLoader(MainApp.class.getResource("scene.fxml"));
            Parent root = fxmlLoader.load();

            // Keep a reference to the controller for graceful shutdown
            controller = fxmlLoader.getController();

            Scene scene = new Scene(root);
            stage.setTitle("Face Recognition");
            stage.setMinWidth(800);
            stage.setMinHeight(500);
            stage.setScene(scene);

            // Ensure the app exits when the window is closed
            stage.setOnCloseRequest(event -> {
                event.consume();
                stage.hide(); // Hide immediately to give feedback to user
                performShutdown();
            });

            stage.show();
            logger.info("Application started successfully.");
        } catch (Throwable e) {
            logger.error("Fatal startup error", e);
            showFatalError(e);
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

        // Watchdog thread: force halt if cleanup hangs for more than 3 seconds
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(3000);
                logger.warn("Shutdown cleanup taking too long, forcing halt...");
                Runtime.getRuntime().halt(0);
            } catch (InterruptedException ignored) {}
        }, "shutdown-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

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