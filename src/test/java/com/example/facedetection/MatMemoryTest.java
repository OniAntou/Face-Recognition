package com.example.facedetection;

import com.example.facedetection.util.MatUtils;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Memory leak detection test for Mat encoding operations.
 * This test verifies that MatOfByte resources are properly released.
 */
class MatMemoryTest {

    static {
        nu.pattern.OpenCV.loadShared();
    }

    private static final int ITERATIONS = 1000;
    private static final double MEMORY_TOLERANCE_MB = 50; // Allow 50MB variance

    @Test
    void testMatToImageMemoryStability() {
        // Create a sample Mat
        Mat testFrame = new Mat(480, 640, CvType.CV_8UC3);
        testFrame.setTo(new org.opencv.core.Scalar(100, 150, 200));

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // Force GC and get baseline
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MemoryUsage baseline = memoryBean.getHeapMemoryUsage();
        long baselineUsed = baseline.getUsed();

        // Simulate repeated encoding operations (like in ViewController.matToImage)
        for (int i = 0; i < ITERATIONS; i++) {
            MatOfByte encodingBuffer = new MatOfByte();
            try {
                Imgcodecs.imencode(".png", testFrame, encodingBuffer);
                byte[] data = encodingBuffer.toArray();
                assertNotNull(data);
                assertTrue(data.length > 0);
            } finally {
                encodingBuffer.release();
            }

            // Periodic GC every 100 iterations
            if (i % 100 == 0) {
                System.gc();
            }
        }

        // Force final GC
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MemoryUsage after = memoryBean.getHeapMemoryUsage();
        long afterUsed = after.getUsed();

        long memoryIncreaseMB = (afterUsed - baselineUsed) / (1024 * 1024);

        System.out.println("Memory Test Results:");
        System.out.println("  Baseline: " + (baselineUsed / (1024 * 1024)) + " MB");
        System.out.println("  After: " + (afterUsed / (1024 * 1024)) + " MB");
        System.out.println("  Increase: " + memoryIncreaseMB + " MB");

        // Memory should not grow significantly
        assertTrue(memoryIncreaseMB < MEMORY_TOLERANCE_MB,
            "Memory increased by " + memoryIncreaseMB + " MB after " + ITERATIONS +
            " iterations. Expected less than " + MEMORY_TOLERANCE_MB + " MB increase.");

        MatUtils.safeRelease(testFrame);
    }

    @Test
    void testMatPoolStressTest() {
        com.example.facedetection.util.MatPool pool =
            new com.example.facedetection.util.MatPool(10, 5);

        // Borrow and return mats repeatedly
        for (int i = 0; i < 500; i++) {
            Mat mat = pool.borrowByteMat();
            assertNotNull(mat);

            // Simulate some work
            mat.create(100, 100, CvType.CV_8UC3);

            pool.returnMat(mat);
        }

        pool.close();

        // If we get here without crash, pool is working correctly
        assertTrue(true);
    }
}
