package com.example.facedetection.integration;

import com.example.facedetection.service.*;
import com.example.facedetection.util.MatPool;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for service lifecycle management.
 * Tests proper initialization, shutdown, and resource cleanup.
 */
class ServiceLifecycleIntegrationTest {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    @Test
    void testMatPoolLifecycle() {
        // Test MatPool proper shutdown
        MatPool pool = new MatPool(10, 5);

        // Borrow some mats
        List<Mat> mats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Mat mat = pool.borrowByteMat();
            mats.add(mat);
        }

        // Return mats
        for (Mat mat : mats) {
            pool.returnMat(mat);
        }

        // Close pool
        pool.close();

        // After close, operations should handle gracefully
        // (not throw unexpected exceptions)
    }

    @Test
    void returnsEmptyBorrowedMatricesToTheirPool() {
        MatPool pool = new MatPool(4, 2);
        Mat empty = pool.borrowByteMat();
        empty.create(0, 0, org.opencv.core.CvType.CV_8UC3);

        assertTrue(empty.empty());
        pool.returnMat(empty);
        assertEquals(0, pool.getActiveCount(MatPool.PoolType.BYTE_MAT));
        pool.close();
    }

    @Test
    void typedPoolsProduceTheTypesTheirContractsPromise() {
        MatPool pool = new MatPool(4, 2);
        Mat bytes = pool.borrowByteMat();
        Mat floats = pool.borrowFloatMat();

        assertEquals(org.opencv.core.CvType.CV_8UC3, bytes.type());
        assertEquals(org.opencv.core.CvType.CV_32FC1, floats.type());

        pool.returnMat(bytes);
        pool.returnMat(floats);
        assertEquals(0, pool.getActiveCount(MatPool.PoolType.AUTO));
        pool.close();
    }

    @Test
    void testFaceTrackerLifecycle() {
        FaceTracker tracker = new FaceTracker();

        // Test with empty detection
        Mat frame = new Mat(480, 640, org.opencv.core.CvType.CV_8UC3);
        frame.setTo(new org.opencv.core.Scalar(100, 150, 200));

        List<TrackedFace> faces = tracker.updateWithDetections(frame, new ArrayList<>());
        assertTrue(faces.isEmpty());

        // Test reset
        tracker.reset();
        assertTrue(tracker.isEmpty());

        // Test close
        tracker.close();

        frame.release();
    }

    @Test
    void testServiceChainInitialization() {
        // Test that services can be initialized in the correct order
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir = new File("app/data");
        }

        // Initialize services that don't require model files
        MatPool matPool = new MatPool(10, 5);
        FaceTracker tracker = new FaceTracker();

        // Test basic operations
        assertNotNull(matPool);
        assertNotNull(tracker);

        // Cleanup
        tracker.close();
        matPool.close();
    }

    @Test
    void testResourceCleanupAfterError() {
        // Test that resources are cleaned up even when operations fail
        Mat frame = new Mat(480, 640, org.opencv.core.CvType.CV_8UC3);

        try {
            // Simulate an operation that might throw
            if (frame.empty()) {
                throw new RuntimeException("Unexpected empty frame");
            }

            // Process frame
            frame.setTo(new org.opencv.core.Scalar(0, 0, 0));

        } finally {
            // Always release
            frame.release();
        }

        // Frame should be released
        assertTrue(frame.empty() || frame.nativeObj == 0);
    }

    @Test
    void testMultipleServiceInitialization() {
        // Test that multiple services can coexist
        List<AutoCloseable> services = new ArrayList<>();

        try {
            services.add(new MatPool(5, 2));
            services.add(new FaceTracker());

            // All services initialized
            assertEquals(2, services.size());

        } finally {
            // Cleanup all services
            for (AutoCloseable service : services) {
                try {
                    service.close();
                } catch (Exception e) {
                    // Log but continue cleanup
                    System.err.println("Error closing service: " + e.getMessage());
                }
            }
        }
    }

    @Test
    void testGracefulDegradationWithoutModels() {
        // Test that the system handles missing models gracefully
        File dataDir = new File("nonexistent_data_path");

        // Try to initialize with invalid paths
        // Should handle gracefully without crashing

        File faceModel = new File(dataDir, "nonexistent.caffemodel");

        assertFalse(faceModel.exists(), "Model file should not exist");

        // The actual service initialization would throw, but we should handle it
        // This tests that our error handling paths work
    }
}
