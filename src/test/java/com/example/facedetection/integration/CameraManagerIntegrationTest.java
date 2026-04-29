package com.example.facedetection.integration;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.service.CameraManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CameraManager with actual frame capture.
 */
class CameraManagerIntegrationTest {

    static {
        nu.pattern.OpenCV.loadShared();
    }

    private CameraManager cameraManager;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        config = AppConfig.getInstance();
        cameraManager = new CameraManager(config);
    }

    @AfterEach
    void tearDown() {
        if (cameraManager != null) {
            cameraManager.close();
        }
    }

    @Test
    void testCameraLifecycle() {
        // Test that camera can be opened, started, stopped, and closed
        assertTrue(cameraManager.open(0), "Should open default camera");

        AtomicInteger frameCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        cameraManager.startCapture(frame -> {
            try {
                if (!frame.empty()) {
                    frameCount.incrementAndGet();
                }
            } finally {
                frame.release();
                latch.countDown();
            }
        });

        // Wait for at least 5 frames
        try {
            boolean received = latch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Should receive frames within 5 seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for frames");
        }

        assertTrue(frameCount.get() >= 3, "Should capture at least 3 valid frames");

        // Stop capture
        cameraManager.stopCapture();
        assertFalse(cameraManager.isActive(), "Camera should be inactive after stop");
    }

    @Test
    void testFrameCloneSafety() throws InterruptedException {
        // Test the race condition fix - frame should be cloned before passing to handler
        assertTrue(cameraManager.open(0), "Should open camera");

        AtomicInteger processedFrames = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(10);

        cameraManager.startCapture(frame -> {
            startLatch.countDown();

            // Simulate async processing that takes time
            new Thread(() -> {
                try {
                    // Simulate some processing
                    Thread.sleep(50);

                    // At this point, the original frame in CameraManager
                    // should not be affected (because it was cloned)
                    if (!frame.empty()) {
                        processedFrames.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    frame.release();
                    completeLatch.countDown();
                }
            }).start();
        });

        // Wait for processing to complete
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS),
            "Should process 10 frames within timeout");

        assertEquals(10, processedFrames.get(),
            "All 10 frames should be processed without corruption");

        cameraManager.stopCapture();
    }

    @Test
    void testCameraReopen() {
        // Test that camera can be reopened after close
        assertTrue(cameraManager.open(0));
        cameraManager.stopCapture();
        cameraManager.close();

        // Should be able to open again
        CameraManager newManager = new CameraManager(config);
        assertTrue(newManager.open(0), "Should be able to reopen camera");
        newManager.close();
    }

    @Test
    void testGrabFrame() {
        // Test single frame grab
        assertTrue(cameraManager.open(0));

        // Grab a few frames
        for (int i = 0; i < 3; i++) {
            Mat frame = cameraManager.grabFrame();
            assertNotNull(frame);

            if (!frame.empty()) {
                assertTrue(frame.cols() > 0);
                assertTrue(frame.rows() > 0);
            }

            frame.release();
        }
    }
}
