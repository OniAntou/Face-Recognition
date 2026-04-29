package com.example.facedetection.util;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MatUtils utility class.
 */
class MatUtilsTest {

    static {
        // Load OpenCV native library
        nu.pattern.OpenCV.loadShared();
    }

    @Test
    void testSafeRelease_NullMat() {
        // Should not throw exception
        assertDoesNotThrow(() -> MatUtils.safeRelease(null));
    }

    @Test
    void testSafeRelease_ValidMat() {
        Mat mat = new Mat(100, 100, org.opencv.core.CvType.CV_8UC3);
        assertFalse(mat.empty());

        MatUtils.safeRelease(mat);

        // Mat should still be empty after release
        // Note: nativeObj becomes 0 after release
        assertTrue(mat.empty() || mat.nativeObj == 0);
    }

    @Test
    void testSafeRelease_AlreadyReleased() {
        Mat mat = new Mat(100, 100, org.opencv.core.CvType.CV_8UC3);
        mat.release();

        // Should not throw exception when releasing already released mat
        assertDoesNotThrow(() -> MatUtils.safeRelease(mat));
    }

    @Test
    void testSafeReleaseAll() {
        Mat mat1 = new Mat(100, 100, org.opencv.core.CvType.CV_8UC3);
        Mat mat2 = new Mat(50, 50, org.opencv.core.CvType.CV_8UC1);
        Mat nullMat = null;

        // Should not throw exception
        assertDoesNotThrow(() -> MatUtils.safeReleaseAll(mat1, mat2, nullMat));
    }

    @Test
    void testIsValid_ValidMat() {
        Mat mat = new Mat(100, 100, org.opencv.core.CvType.CV_8UC3);
        assertTrue(MatUtils.isValid(mat));
        mat.release();
    }

    @Test
    void testIsValid_NullMat() {
        assertFalse(MatUtils.isValid(null));
    }

    @Test
    void testIsValid_EmptyMat() {
        Mat mat = new Mat();
        assertFalse(MatUtils.isValid(mat));
    }

    @Test
    void testArea_ValidMat() {
        Mat mat = new Mat(100, 200, org.opencv.core.CvType.CV_8UC3);
        assertEquals(20000, MatUtils.area(mat));
        mat.release();
    }

    @Test
    void testArea_InvalidMat() {
        assertEquals(0, MatUtils.area(null));
        assertEquals(0, MatUtils.area(new Mat()));
    }
}
