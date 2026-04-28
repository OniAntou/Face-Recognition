package com.example.facedetection.service;

import org.junit.jupiter.api.Test;
import org.opencv.core.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoloFaceServiceTest {

    @Test
    void extractsLandmarksFromChannelMajorOutput() {
        float[][] data = new float[15][1];
        data[5][0] = 0.10f;
        data[6][0] = 0.20f;
        data[7][0] = 0.30f;
        data[8][0] = 0.40f;
        data[9][0] = 0.50f;
        data[10][0] = 0.60f;
        data[11][0] = 0.70f;
        data[12][0] = 0.80f;
        data[13][0] = 0.90f;
        data[14][0] = 1.00f;

        Point[] landmarks = YoloFaceService.extractLandmarks(data, false, 0, 15, 640f, 480f);

        assertEquals(64.0, landmarks[0].x, 0.001);
        assertEquals(96.0, landmarks[0].y, 0.001);
        assertEquals(576.0, landmarks[4].x, 0.001);
        assertEquals(480.0, landmarks[4].y, 0.001);
    }

    @Test
    void extractsLandmarksFromPredictionMajorOutput() {
        float[][] data = new float[1][15];
        data[0][5] = 0.25f;
        data[0][6] = 0.35f;
        data[0][7] = 0.45f;
        data[0][8] = 0.55f;
        data[0][9] = 0.65f;
        data[0][10] = 0.75f;
        data[0][11] = 0.85f;
        data[0][12] = 0.95f;
        data[0][13] = 1.05f;
        data[0][14] = 1.15f;

        Point[] landmarks = YoloFaceService.extractLandmarks(data, true, 0, 15, 320f, 240f);

        assertEquals(80.0, landmarks[0].x, 0.001);
        assertEquals(84.0, landmarks[0].y, 0.001);
        assertEquals(336.0, landmarks[4].x, 0.001);
        assertEquals(276.0, landmarks[4].y, 0.001);
    }
}
