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
}
