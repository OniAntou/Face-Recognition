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

    @Override
    public void init() {
        // Register a safety-net shutdown hook that will forcibly kill the JVM
        // if the normal stop() flow hangs (e.g. OpenCV native thread stuck).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook: forcing halt in 3 seconds if still alive...");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                // interrupted = we're already shutting down fast
            }
            // If we reach here, something is blocking JVM exit — force kill
            Runtime.getRuntime().halt(0);
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
                event.consume();          // We handle shutdown ourselves
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
        logger.info("Application shutting down...");

        Thread shutdownThread = new Thread(() -> {
            // Step 1: Try to gracefully release resources
            if (controller != null) {
                try {
                    controller.shutdown();
                } catch (Exception e) {
                    logger.error("Error during controller shutdown", e);
                }
            }

            // Step 2: Force JVM exit — this will trigger the shutdown hook timer
            // as a safety net in case System.exit() gets stuck on native threads.
            System.exit(0);
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
        // This may also be called by Platform.exit() — guard with performShutdown
        performShutdown();
    }

    public static javafx.application.HostServices getHostServicesInstance() {
        return hostServicesInstance;
    }

    public static void main(String[] args) {
        launch(args);
    }
}