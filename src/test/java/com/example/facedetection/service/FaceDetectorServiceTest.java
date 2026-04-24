package com.example.facedetection.service;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import java.nio.file.Path;
import java.nio.file.Paths;

class FaceDetectorServiceTest {

    @BeforeAll
    static void loadOpenCV() {
        OpenCV.loadShared();
    }

    private Mat image;

    @AfterEach
    void cleanup() {
        if (image != null) {
            image.release();
            image = null;
        }
    }

    @Test
    void safeReleaseShouldIgnoreNullAndAlreadyReleasedMat() throws Exception {
        var safeRelease = FaceDetectorService.class.getDeclaredMethod("safeRelease", Mat.class);
        safeRelease.setAccessible(true);

        Assertions.assertDoesNotThrow(() -> safeRelease.invoke(null, (Mat) null));

        Mat mat = new Mat();
        mat.release();
        Assertions.assertDoesNotThrow(() -> safeRelease.invoke(null, mat));
    }

    @Test
    void detectFacesReturnsEmptyForBlankImage() {
        FaceDetectorService service = createService();

        image = new Mat(300, 300, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Rect[] faces = service.detectFaces(image);

        Assertions.assertNotNull(faces);
        Assertions.assertEquals(0, faces.length);
    }

    @Test
    void detectAndDrawFacesReturnsZeroWhenNoFacesPresent() {
        FaceDetectorService service = createService();

        image = new Mat(300, 300, CvType.CV_8UC3, new Scalar(0, 0, 0));
        int count = service.detectAndDrawFaces(image);

        Assertions.assertEquals(0, count);
        Assertions.assertFalse(image.empty());
    }

    private FaceDetectorService createService() {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path dataDir = root.resolve("data");

        String faceModelPath = dataDir.resolve("res10_300x300_ssd_iter_140000.caffemodel").toString();
        String faceConfigPath = dataDir.resolve("deploy.prototxt").toString();
        String genderModelPath = dataDir.resolve("gender_net.caffemodel").toString();
        String genderConfigPath = dataDir.resolve("gender_deploy.prototxt").toString();

        return new FaceDetectorService(faceModelPath, faceConfigPath, genderModelPath, genderConfigPath, 0.55f);
    }
}
