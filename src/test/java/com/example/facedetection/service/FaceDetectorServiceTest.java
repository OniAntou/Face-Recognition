package com.example.facedetection.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FaceDetectorServiceTest {

    private static FaceDetectorService service;

    @BeforeAll
    static void setup() {
        // Load OpenCV native library using OpenPNP loader
        nu.pattern.OpenCV.loadShared();
        
        String dataPath = "data/";
        service = new FaceDetectorService(
                dataPath + "res10_300x300_ssd_iter_140000.caffemodel",
                dataPath + "deploy.prototxt",
                dataPath + "gender_net.caffemodel",
                dataPath + "gender_deploy.prototxt"
        );
    }

    @Test
    void testDetectFaces() {
        // Create a blank image (no faces)
        Mat blank = Mat.zeros(300, 300, org.opencv.core.CvType.CV_8UC3);
        Rect[] faces = service.detectFaces(blank);
        assertEquals(0, faces.length, "Should detect 0 faces in a blank image");
        blank.release();
    }

    @Test
    void testDetectAndDraw() {
        Mat blank = Mat.zeros(300, 300, org.opencv.core.CvType.CV_8UC3);
        int count = service.detectAndDrawFaces(blank);
        assertEquals(0, count);
        blank.release();
    }

    @Test
    void testRealImagesFromDirectory() {
        File testDir = new File("test_images");
        if (!testDir.exists() || !testDir.isDirectory()) {
            System.out.println("Skipping real image tests: 'test_images' directory not found.");
            return;
        }

        File[] files = testDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png"));
        if (files == null || files.length == 0) {
            System.out.println("Skipping real image tests: No images found in 'test_images'.");
            return;
        }

        for (File file : files) {
            Mat img = Imgcodecs.imread(file.getAbsolutePath());
            assertFalse(img.empty(), "Failed to load test image: " + file.getName());
            
            try {
                Rect[] faces = service.detectFaces(img);
                System.out.println("Detected " + faces.length + " faces in " + file.getName());
                // We don't assert count since we don't know the ground truth, but it shouldn't crash
            } finally {
                img.release();
            }
        }
    }
}
