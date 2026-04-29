package com.example.facedetection.integration;

import com.example.facedetection.util.MatPool;
import com.example.facedetection.util.MatUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for end-to-end frame processing flow.
 * Tests the complete pipeline from frame capture to image encoding.
 */
class FrameProcessingIntegrationTest {

    static {
        nu.pattern.OpenCV.loadShared();
    }

    private MatPool matPool;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        matPool = new MatPool(10, 5);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-ai-processor");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        if (matPool != null) {
            matPool.close();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testMatPoolIntegration() {
        // Test borrowing and returning mats through pool
        Mat borrowed = matPool.borrowByteMat();
        assertNotNull(borrowed);

        // Simulate processing
        borrowed.create(100, 100, CvType.CV_8UC3);
        borrowed.setTo(new org.opencv.core.Scalar(100, 150, 200));

        // Return to pool
        matPool.returnMat(borrowed);

        // Borrow again - should get the same mat (or a valid one)
        Mat borrowed2 = matPool.borrowByteMat();
        assertNotNull(borrowed2);
        borrowed2.release();
    }

    @Test
    void testConcurrentMatOperations() throws InterruptedException {
        // Test concurrent mat operations (simulating camera + AI thread)
        int iterations = 100;
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Exception> error = new AtomicReference<>();

        for (int i = 0; i < iterations; i++) {
            final int frameNum = i;
            executor.submit(() -> {
                try {
                    // Simulate frame processing
                    Mat frame = new Mat(480, 640, CvType.CV_8UC3);
                    frame.setTo(new org.opencv.core.Scalar(
                        frameNum % 255, (frameNum * 2) % 255, (frameNum * 3) % 255
                    ));

                    // Simulate resize
                    Mat resized = new Mat();
                    Imgproc.resize(frame, resized, new Size(320, 240));

                    // Simulate encoding (like ViewController.matToImage)
                    MatOfByte buffer = new MatOfByte();
                    try {
                        Imgcodecs.imencode(".png", resized, buffer);
                        byte[] data = buffer.toArray();

                        if (data != null && data.length > 0) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        buffer.release();
                    }

                    MatUtils.safeRelease(frame);
                    MatUtils.safeRelease(resized);

                } catch (Exception e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS),
            "All operations should complete within timeout");

        assertNull(error.get(), "No exceptions should occur: " + error.get());
        assertEquals(iterations, successCount.get(),
            "All " + iterations + " operations should succeed");
    }

    @Test
    void testFrameCloneAndRelease() {
        // Test the race condition fix pattern used in CameraManager
        Mat original = new Mat(480, 640, CvType.CV_8UC3);
        original.setTo(new org.opencv.core.Scalar(100, 150, 200));

        // Simulate: clone, pass to handler, release original
        Mat clone = original.clone();

        // Handler uses clone
        assertFalse(clone.empty());
        assertEquals(original.cols(), clone.cols());
        assertEquals(original.rows(), clone.rows());

        // Original released (simulating CameraManager)
        original.release();

        // Clone should still be valid
        assertFalse(clone.empty());

        // Handler releases clone
        clone.release();
    }

    @Test
    void testAsyncFrameProcessing() throws InterruptedException {
        // Simulate async frame processing like in ViewController
        BlockingQueue<Mat> frameQueue = new LinkedBlockingQueue<>(5);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch processLatch = new CountDownLatch(10);

        // Producer thread (simulates camera)
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                Mat frame = new Mat(480, 640, CvType.CV_8UC3);
                frame.setTo(new org.opencv.core.Scalar(i * 20, i * 25, i * 30));

                // Clone before putting in queue (race condition fix)
                Mat frameCopy = frame.clone();

                try {
                    frameQueue.put(frameCopy);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    MatUtils.safeRelease(frameCopy);
                    break;
                }

                frame.release();

                try {
                    Thread.sleep(33); // ~30fps
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "test-producer");

        // Consumer thread (simulates AI processing)
        Thread consumer = new Thread(() -> {
            while (processLatch.getCount() > 0 && !Thread.currentThread().isInterrupted()) {
                try {
                    Mat frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        // Simulate processing
                        Thread.sleep(20);

                        processedCount.incrementAndGet();
                        frame.release();
                        processLatch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "test-consumer");

        producer.start();
        consumer.start();

        assertTrue(processLatch.await(5, TimeUnit.SECONDS),
            "Should process 10 frames within timeout");

        producer.interrupt();
        consumer.interrupt();

        assertEquals(10, processedCount.get(), "All 10 frames should be processed");
    }

    @Test
    void testMatToImageMemoryStability() {
        // Test that matToImage pattern doesn't leak memory
        Mat testFrame = new Mat(480, 640, CvType.CV_8UC3);
        testFrame.setTo(new org.opencv.core.Scalar(100, 150, 200));

        // Get baseline memory
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Simulate many encoding operations
        for (int i = 0; i < 500; i++) {
            MatOfByte encodingBuffer = new MatOfByte();
            try {
                Imgcodecs.imencode(".png", testFrame, encodingBuffer);
                byte[] data = encodingBuffer.toArray();
                assertNotNull(data);
            } finally {
                encodingBuffer.release();
            }
        }

        System.gc();
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();

        // Memory should not grow significantly (allow 20MB tolerance)
        long memoryIncreaseMB = (afterMemory - baselineMemory) / (1024 * 1024);
        assertTrue(memoryIncreaseMB < 20,
            "Memory increased by " + memoryIncreaseMB + " MB after 500 iterations");

        testFrame.release();
    }
}
