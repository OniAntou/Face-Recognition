package com.example.facedetection.ui;

import com.example.facedetection.service.CameraManager;
import com.example.facedetection.service.UpdateService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages UI updates and state for the face recognition application.
 * Decouples UI manipulation from processing logic.
 */
public class UIManager {

    private static final Logger logger = LoggerFactory.getLogger(UIManager.class);

    // UI Components
    private final ImageView originalImageView;
    private final ImageView processedImageView;
    private final StackPane sourceViewport;
    private final StackPane resultViewport;
    private final VBox sourceCard;
    private final Button cameraButton;
    private final ComboBox<String> cameraSelector;
    private final Label statusLabel;
    private final Label engineLabel;
    private final Label fpsLabel;
    private final Label exposureStatusLabel;
    private final CheckBox adaptiveExposureCheckBox;
    private final CheckBox brightLightModeCheckBox;
    private final CheckBox genderRecognitionCheckBox;

    // Services
    private final CameraManager cameraManager;
    private final UpdateService updateService;

    public UIManager(ImageView originalImageView, ImageView processedImageView,
                     StackPane sourceViewport, StackPane resultViewport,
                     VBox sourceCard, Button cameraButton, ComboBox<String> cameraSelector,
                     Label statusLabel, Label engineLabel, Label fpsLabel,
                     Label exposureStatusLabel, CheckBox adaptiveExposureCheckBox,
                     CheckBox brightLightModeCheckBox,
                     CheckBox genderRecognitionCheckBox,
                     CameraManager cameraManager, UpdateService updateService) {
        this.originalImageView = originalImageView;
        this.processedImageView = processedImageView;
        this.sourceViewport = sourceViewport;
        this.resultViewport = resultViewport;
        this.sourceCard = sourceCard;
        this.cameraButton = cameraButton;
        this.cameraSelector = cameraSelector;
        this.statusLabel = statusLabel;
        this.engineLabel = engineLabel;
        this.fpsLabel = fpsLabel;
        this.exposureStatusLabel = exposureStatusLabel;
        this.adaptiveExposureCheckBox = adaptiveExposureCheckBox;
        this.brightLightModeCheckBox = brightLightModeCheckBox;
        this.genderRecognitionCheckBox = genderRecognitionCheckBox;
        this.cameraManager = cameraManager;
        this.updateService = updateService;

        bindImageViews();
        initializeLabels();
    }

    private void bindImageViews() {
        if (originalImageView != null && sourceViewport != null) {
            originalImageView.fitWidthProperty().bind(sourceViewport.widthProperty().subtract(16));
            originalImageView.fitHeightProperty().bind(sourceViewport.heightProperty().subtract(16));
        }
        if (processedImageView != null && resultViewport != null) {
            processedImageView.fitWidthProperty().bind(resultViewport.widthProperty().subtract(16));
            processedImageView.fitHeightProperty().bind(resultViewport.heightProperty().subtract(16));
        }
    }

    private void initializeLabels() {
        if (fpsLabel != null) {
            fpsLabel.setText("FPS: --");
        }
        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText("Exposure: Ready");
        }
    }

    /**
     * Updates the UI with processed frame results.
     * Must be called on JavaFX Application Thread.
     *
     * @param originalImage original camera/image frame
     * @param processedImage annotated frame with detections
     * @param statusText status message
     * @param engineLabelText current detection engine label
     * @param fps current FPS value
     * @param exposureStatus exposure control status
     */
    public void updateFrame(Image originalImage, Image processedImage,
                           String statusText, String engineLabelText,
                           double fps, String exposureStatus) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Must be called on FX Application Thread");
        }

        if (!cameraManager.isActive()) {
            return;
        }

        originalImageView.setImage(originalImage);
        processedImageView.setImage(processedImage);

        if (!updateService.isUpdateAvailable()) {
            statusLabel.setText(statusText);
        }
        if (engineLabel != null) {
            engineLabel.setText(engineLabelText);
        }
        if (fpsLabel != null) {
            fpsLabel.setText(String.format("FPS: %.1f", fps));
        }
        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText(exposureStatus);
        }
    }

    /**
     * Updates UI for static image processing.
     *
     * @param originalImage original image
     * @param processedImage annotated image
     * @param statusText status message
     * @param engineLabelText detection engine label
     */
    public void updateStaticImage(Image originalImage, Image processedImage,
                                  String statusText, String engineLabelText) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Must be called on FX Application Thread");
        }

        sourceCard.setVisible(true);
        sourceCard.setManaged(true);
        originalImageView.setImage(originalImage);
        processedImageView.setImage(processedImage);

        if (!updateService.isUpdateAvailable()) {
            statusLabel.setText(statusText);
        }
        if (engineLabel != null) {
            engineLabel.setText(engineLabelText);
        }
        if (fpsLabel != null) {
            fpsLabel.setText("FPS: Static");
        }
        if (exposureStatusLabel != null) {
            exposureStatusLabel.setText("Exposure: N/A for image");
        }
    }

    /**
     * Sets camera button state for active camera.
     */
    public void setCameraActive() {
        cameraButton.setText("\u23F9  Stop Camera");
        cameraButton.getStyleClass().remove("btn-primary");
        cameraButton.getStyleClass().add("btn-danger");
        sourceCard.setVisible(false);
        sourceCard.setManaged(false);
    }

    /**
     * Sets camera button state for inactive camera.
     */
    public void setCameraInactive() {
        cameraButton.setText("\u23FA  Start Camera");
        cameraButton.getStyleClass().remove("btn-danger");
        cameraButton.getStyleClass().add("btn-primary");
        sourceCard.setVisible(true);
        sourceCard.setManaged(true);
    }

    /**
     * Updates camera selector items.
     *
     * @param cameraNames list of camera names
     */
    public void updateCameraSelector(java.util.List<String> cameraNames) {
        cameraSelector.getItems().setAll(cameraNames);
    }

    /**
     * Selects camera by index.
     *
     * @param index camera index to select
     */
    public void selectCamera(int index) {
        if (index < cameraSelector.getItems().size()) {
            cameraSelector.getSelectionModel().select(index);
        } else {
            cameraSelector.getSelectionModel().selectFirst();
        }
    }

    /**
     * Gets currently selected camera index.
     *
     * @return selected camera index
     */
    public int getSelectedCameraIndex() {
        String selected = cameraSelector.getSelectionModel().getSelectedItem();
        if (selected != null && selected.contains("Camera ")) {
            try {
                return Integer.parseInt(selected.replace("Camera ", ""));
            } catch (NumberFormatException e) {
                logger.warn("Invalid camera selection: {}", selected);
            }
        }
        return 0;
    }

    /**
     * Shows error alert dialog.
     *
     * @param title alert title
     * @param message alert message
     */
    public void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Updates status bar with update notification.
     *
     * @param message status message
     * @param onClick callback when status is clicked
     */
    public void showUpdateNotification(String message, Runnable onClick) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: #FFCC00; -fx-cursor: hand;");
            statusLabel.setOnMouseClicked(event -> onClick.run());
        });
    }

    /**
     * Resets status bar style.
     */
    public void resetStatusStyle() {
        statusLabel.setStyle("");
        statusLabel.setOnMouseClicked(null);
    }

    // Getters for checkboxes (for state checking)
    public boolean isAdaptiveExposureEnabled() {
        return adaptiveExposureCheckBox == null || adaptiveExposureCheckBox.isSelected();
    }

    public boolean isBrightLightModeEnabled() {
        return brightLightModeCheckBox == null || brightLightModeCheckBox.isSelected();
    }

    public boolean isGenderRecognitionEnabled() {
        return genderRecognitionCheckBox == null || genderRecognitionCheckBox.isSelected();
    }

    public CheckBox getAdaptiveExposureCheckBox() {
        return adaptiveExposureCheckBox;
    }

    public CheckBox getBrightLightModeCheckBox() {
        return brightLightModeCheckBox;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }
}
