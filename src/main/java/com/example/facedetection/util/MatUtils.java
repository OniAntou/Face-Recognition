package com.example.facedetection.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelFormat;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

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

    /**
     * Efficiently converts an OpenCV Mat to a JavaFX Image without encoding.
     * Supports BGR (3 channels) and Grayscale (1 channel).
     *
     * @param mat the source Mat
     * @return JavaFX Image
     */
    public static Image matToImageDirect(Mat mat) {
        if (!isValid(mat)) {
            return null;
        }

        int width = mat.cols();
        int height = mat.rows();
        int channels = mat.channels();
        
        // Ensure we only process 8-bit Mats for byte-buffer conversion
        if (mat.depth() != org.opencv.core.CvType.CV_8U) {
            return null;
        }

        byte[] buffer = new byte[width * height * channels];
        mat.get(0, 0, buffer);

        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter pw = writableImage.getPixelWriter();

        if (channels == 3) {
            // OpenCV uses BGR, JavaFX getByteRgbInstance expects RGB.
            // We swap B and R while copying to the buffer.
            for (int i = 0; i < buffer.length; i += 3) {
                byte b = buffer[i];
                byte r = buffer[i + 2];
                buffer[i] = r;     // R
                buffer[i + 2] = b; // B
            }
            pw.setPixels(0, 0, width, height, PixelFormat.getByteRgbInstance(), buffer, 0, width * 3);
        } else if (channels == 1) {
            // For grayscale, we need to map to RGB or use a gray pixel format if available
            // PixelFormat.getByteRgbInstance() doesn't support gray directly, so we use a loop or a trick
            byte[] rgbBuffer = new byte[width * height * 3];
            for (int i = 0; i < buffer.length; i++) {
                rgbBuffer[i * 3] = buffer[i];
                rgbBuffer[i * 3 + 1] = buffer[i];
                rgbBuffer[i * 3 + 2] = buffer[i];
            }
            pw.setPixels(0, 0, width, height, PixelFormat.getByteRgbInstance(), rgbBuffer, 0, width * 3);
        }

        return writableImage;
    }
}
