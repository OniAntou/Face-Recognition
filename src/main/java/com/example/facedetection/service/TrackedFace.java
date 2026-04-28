package com.example.facedetection.service;

import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * Public snapshot of a tracked face in the current frame.
 */
public record TrackedFace(int id, Rect boundingBox, Point[] landmarks, double confidence, boolean trackedOnly) {

    public TrackedFace {
        boundingBox = boundingBox == null ? new Rect() : boundingBox;
        landmarks = cloneLandmarks(landmarks);
    }

    public Point[] landmarksCopy() {
        return cloneLandmarks(landmarks);
    }

    private static Point[] cloneLandmarks(Point[] source) {
        if (source == null || source.length == 0) {
            return new Point[0];
        }

        Point[] copy = new Point[source.length];
        for (int i = 0; i < source.length; i++) {
            Point point = source[i];
            copy[i] = point == null ? null : new Point(point.x, point.y);
        }
        return copy;
    }
}
