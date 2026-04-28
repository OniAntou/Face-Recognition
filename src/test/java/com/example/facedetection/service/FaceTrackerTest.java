package com.example.facedetection.service;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceTrackerTest {

    @BeforeAll
    static void setup() {
        OpenCV.loadShared();
    }

    @Test
    void keepsStableIdsWhenDetectionOrderChanges() {
        FaceTracker tracker = new FaceTracker();
        Mat frame = Mat.zeros(240, 320, CvType.CV_8UC3);
        try {
            List<TrackedFace> first = tracker.updateWithDetections(frame, List.of(
                    new FaceDetection(new Rect(30, 40, 60, 60), new Point[0], 0.9f, "YOLOv8"),
                    new FaceDetection(new Rect(180, 50, 60, 60), new Point[0], 0.9f, "YOLOv8")));

            List<TrackedFace> second = tracker.updateWithDetections(frame, List.of(
                    new FaceDetection(new Rect(182, 52, 60, 60), new Point[0], 0.9f, "YOLOv8"),
                    new FaceDetection(new Rect(32, 42, 60, 60), new Point[0], 0.9f, "YOLOv8")));

            assertEquals(2, first.size());
            assertEquals(2, second.size());
            assertEquals(first.get(0).id(), second.get(0).boundingBox().x < 100 ? second.get(0).id() : second.get(1).id());
            assertEquals(first.get(1).id(), second.get(0).boundingBox().x > 100 ? second.get(0).id() : second.get(1).id());
        } finally {
            tracker.close();
            frame.release();
        }
    }

    @Test
    void tracksFaceBetweenDetectionPasses() {
        FaceTracker tracker = new FaceTracker();
        Mat frame1 = Mat.zeros(220, 320, CvType.CV_8UC3);
        Mat frame2 = Mat.zeros(220, 320, CvType.CV_8UC3);
        try {
            Imgproc.rectangle(frame1, new Point(40, 60), new Point(100, 120), new Scalar(255, 255, 255), -1);
            Imgproc.rectangle(frame2, new Point(62, 60), new Point(122, 120), new Scalar(255, 255, 255), -1);

            tracker.updateWithDetections(frame1, List.of(
                    new FaceDetection(new Rect(40, 60, 60, 60), new Point[0], 0.95f, "YOLOv8")));

            List<TrackedFace> tracked = tracker.track(frame2);
            assertFalse(tracked.isEmpty(), "Should track the face in the second frame");
            assertTrue(tracked.get(0).trackedOnly(), "Should be marked as tracked-only");
        } finally {
            tracker.close();
            frame1.release();
            frame2.release();
        }
    }
}
