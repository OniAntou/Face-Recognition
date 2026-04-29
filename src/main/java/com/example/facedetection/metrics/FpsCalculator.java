package com.example.facedetection.metrics;

import com.example.facedetection.config.AppConfig;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calculates smoothed FPS (frames per second) for video processing.
 * Uses exponential smoothing to reduce jitter in FPS display.
 */
public class FpsCalculator {

    private final AppConfig config;
    private final AtomicLong lastTimestampNanos;
    private final AtomicReference<Double> smoothedFps;

    public FpsCalculator(AppConfig config) {
        this.config = config;
        this.lastTimestampNanos = new AtomicLong(0);
        this.smoothedFps = new AtomicReference<>(0.0);
    }

    /**
     * Updates FPS calculation with current frame timestamp.
     * Should be called for each processed frame.
     *
     * @return current smoothed FPS value
     */
    public double update() {
        long now = System.nanoTime();
        long previous = lastTimestampNanos.getAndSet(now);

        if (previous == 0L) {
            // First frame, return current smoothed value
            return smoothedFps.get();
        }

        // Calculate instantaneous FPS
        double instantaneous = 1_000_000_000.0 / Math.max(1L, now - previous);
        double current = smoothedFps.get();

        // Apply exponential smoothing
        double newFps = current == 0.0
                ? instantaneous
                : current * config.fpsSmoothingFactor + instantaneous * (1.0 - config.fpsSmoothingFactor);

        smoothedFps.set(newFps);
        return newFps;
    }

    /**
     * Gets the current smoothed FPS without updating.
     *
     * @return current FPS value
     */
    public double getCurrentFps() {
        return smoothedFps.get();
    }

    /**
     * Resets the FPS calculator to initial state.
     */
    public void reset() {
        lastTimestampNanos.set(0);
        smoothedFps.set(0.0);
    }

    /**
     * Formats FPS value for display.
     *
     * @return formatted string (e.g., "30.5")
     */
    public String formatFps() {
        return String.format("%.1f", smoothedFps.get());
    }
}
