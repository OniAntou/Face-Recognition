package com.example.facedetection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Service for persisting user preferences across application sessions.
 * Uses Java Preferences API for cross-platform storage.
 */
public class PreferencesService {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesService.class);
    private static final String PREF_NODE = "com/example/facedetection";

    // Preference keys
    private static final String KEY_LAST_CAMERA = "last_camera_index";
    private static final String KEY_ADAPTIVE_EXPOSURE = "adaptive_exposure_enabled";
    private static final String KEY_BRIGHT_LIGHT_MODE = "bright_light_mode_enabled";
    private static final String KEY_WINDOW_WIDTH = "window_width";
    private static final String KEY_WINDOW_HEIGHT = "window_height";
    private static final String KEY_WINDOW_MAXIMIZED = "window_maximized";
    private static final String KEY_GENDER_RECOGNITION = "gender_recognition_enabled";

    private final Preferences prefs;

    public PreferencesService() {
        this.prefs = Preferences.userRoot().node(PREF_NODE);
        logger.info("Preferences service initialized");
    }

    // Camera preferences

    public void setLastCameraIndex(int index) {
        prefs.putInt(KEY_LAST_CAMERA, index);
        flush();
    }

    public int getLastCameraIndex(int defaultValue) {
        return prefs.getInt(KEY_LAST_CAMERA, defaultValue);
    }

    // Feature toggles

    public void setAdaptiveExposureEnabled(boolean enabled) {
        prefs.putBoolean(KEY_ADAPTIVE_EXPOSURE, enabled);
        flush();
    }

    public boolean isAdaptiveExposureEnabled(boolean defaultValue) {
        return prefs.getBoolean(KEY_ADAPTIVE_EXPOSURE, defaultValue);
    }

    public void setBrightLightModeEnabled(boolean enabled) {
        prefs.putBoolean(KEY_BRIGHT_LIGHT_MODE, enabled);
        flush();
    }

    public boolean isBrightLightModeEnabled(boolean defaultValue) {
        return prefs.getBoolean(KEY_BRIGHT_LIGHT_MODE, defaultValue);
    }

    public void setGenderRecognitionEnabled(boolean enabled) {
        prefs.putBoolean(KEY_GENDER_RECOGNITION, enabled);
        flush();
    }

    public boolean isGenderRecognitionEnabled(boolean defaultValue) {
        return prefs.getBoolean(KEY_GENDER_RECOGNITION, defaultValue);
    }

    // Window state

    public void setWindowSize(double width, double height) {
        prefs.putDouble(KEY_WINDOW_WIDTH, width);
        prefs.putDouble(KEY_WINDOW_HEIGHT, height);
        flush();
    }

    public Optional<double[]> getWindowSize() {
        double width = prefs.getDouble(KEY_WINDOW_WIDTH, -1);
        double height = prefs.getDouble(KEY_WINDOW_HEIGHT, -1);

        if (width > 0 && height > 0) {
            return Optional.of(new double[]{width, height});
        }
        return Optional.empty();
    }

    public void setWindowMaximized(boolean maximized) {
        prefs.putBoolean(KEY_WINDOW_MAXIMIZED, maximized);
        flush();
    }

    public boolean isWindowMaximized(boolean defaultValue) {
        return prefs.getBoolean(KEY_WINDOW_MAXIMIZED, defaultValue);
    }

    // Generic methods

    public void setString(String key, String value) {
        prefs.put(key, value);
        flush();
    }

    public String getString(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public void setInt(String key, int value) {
        prefs.putInt(key, value);
        flush();
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.putBoolean(key, value);
        flush();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setDouble(String key, double value) {
        prefs.putDouble(key, value);
        flush();
    }

    public double getDouble(String key, double defaultValue) {
        return prefs.getDouble(key, defaultValue);
    }

    /**
     * Clears all preferences.
     */
    public void clear() {
        try {
            prefs.clear();
            flush();
            logger.info("All preferences cleared");
        } catch (Exception e) {
            logger.error("Failed to clear preferences: {}", e.getMessage());
        }
    }

    /**
     * Ensures preferences are written to persistent storage.
     */
    private void flush() {
        try {
            prefs.flush();
        } catch (Exception e) {
            logger.warn("Failed to flush preferences: {}", e.getMessage());
        }
    }

    /**
     * Exports all preferences as a debug string.
     */
    public String exportDebug() {
        StringBuilder sb = new StringBuilder("Preferences:\n");
        try {
            for (String key : prefs.keys()) {
                sb.append("  ").append(key).append(" = ");
                // Handle different types appropriately
                if (key.contains("enabled") || key.contains("maximized")) {
                    sb.append(prefs.getBoolean(key, false));
                } else if (key.contains("width") || key.contains("height")) {
                    sb.append(prefs.getDouble(key, -1));
                } else if (key.contains("index")) {
                    sb.append(prefs.getInt(key, -1));
                } else {
                    sb.append(prefs.get(key, ""));
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            sb.append("  [Error reading preferences: ").append(e.getMessage()).append("]");
        }
        return sb.toString();
    }
}
