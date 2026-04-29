package com.example.facedetection.util;

import org.opencv.core.Mat;

/**
 * Utility class for OpenCV Mat operations.
 * Provides safe release and other common Mat utilities.
 */
public final class MatUtils {

    private MatUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Safely releases a Mat object, ignoring any exceptions.
     * Use this when the Mat may already be released or null.
     *
     * @param mat the Mat to release, may be null
     */
    public static void safeRelease(Mat mat) {
        if (mat != null) {
            try {
                mat.release();
            } catch (Exception ignored) {
                // Ignore already released or invalid mats
            }
        }
    }

    /**
     * Safely releases multiple Mat objects at once.
     *
     * @param mats varargs of Mats to release
     */
    public static void safeReleaseAll(Mat... mats) {
        if (mats == null) {
            return;
        }
        for (Mat mat : mats) {
            safeRelease(mat);
        }
    }

    /**
     * Checks if a Mat is valid (not null and not empty).
     *
     * @param mat the Mat to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(Mat mat) {
        return mat != null && !mat.empty();
    }

    /**
     * Gets the area of a Mat if valid, 0 otherwise.
     *
     * @param mat the Mat
     * @return width * height or 0
     */
    public static int area(Mat mat) {
        return isValid(mat) ? mat.cols() * mat.rows() : 0;
    }
}
