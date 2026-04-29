package com.example.facedetection.service;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight face tracker that keeps stable face IDs between heavier detection passes.
 * It combines greedy IoU matching with local template matching.
 */
public class FaceTracker implements AutoCloseable {

    private static final double IOU_ASSOCIATION_THRESHOLD = 0.45;
    private static final double TRACK_MATCH_THRESHOLD = 0.58;
    private static final double SEARCH_EXPANSION_RATIO = 0.45;
    private static final double RECT_SMOOTHING = 0.60;
    private static final int MAX_MISSED_FRAMES = 6;

    private final List<TrackState> tracks = new ArrayList<>();
    private int nextTrackId = 1;

    public synchronized List<TrackedFace> updateWithDetections(Mat frame, List<FaceDetection> detections) {
        Mat gray = toGray(frame);
        try {
            boolean[] matchedTracks = new boolean[tracks.size()];
            boolean[] matchedDetections = new boolean[detections.size()];
            List<AssociationCandidate> candidates = buildCandidates(detections);

            candidates.sort(Comparator.comparingDouble(AssociationCandidate::score).reversed());
            for (AssociationCandidate candidate : candidates) {
                if (matchedTracks[candidate.trackIndex] || matchedDetections[candidate.detectionIndex]) {
                    continue;
                }

                matchedTracks[candidate.trackIndex] = true;
                matchedDetections[candidate.detectionIndex] = true;
                TrackState track = tracks.get(candidate.trackIndex);
                FaceDetection detection = detections.get(candidate.detectionIndex);
                updateTrackFromDetection(track, detection, gray, false, candidate.score);
            }

            for (int i = 0; i < tracks.size(); i++) {
                if (!matchedTracks[i]) {
                    tracks.get(i).missedFrames++;
                }
            }

            for (int i = 0; i < detections.size(); i++) {
                if (!matchedDetections[i]) {
                    createTrack(detections.get(i), gray);
                }
            }

            pruneLostTracks();
            return snapshot();
        } finally {
            gray.release();
        }
    }

    public synchronized List<TrackedFace> track(Mat frame) {
        Mat gray = toGray(frame);
        try {
            for (TrackState track : tracks) {
                if (track.templateGray.empty()) {
                    track.missedFrames++;
                    continue;
                }

                Rect safeRect = clampRect(track.boundingBox, gray.cols(), gray.rows());
                if (safeRect.width < 8 || safeRect.height < 8) {
                    track.missedFrames++;
                    continue;
                }

                Rect searchRect = expandRect(safeRect, gray.cols(), gray.rows(), SEARCH_EXPANSION_RATIO);
                if (searchRect.width < track.templateGray.cols() || searchRect.height < track.templateGray.rows()) {
                    track.missedFrames++;
                    continue;
                }

                Mat searchRegion = new Mat(gray, searchRect);
                Mat result = new Mat();
                try {
                    Imgproc.matchTemplate(searchRegion, track.templateGray, result, Imgproc.TM_CCOEFF_NORMED);
                    Core.MinMaxLocResult mm = Core.minMaxLoc(result);
                    if (mm.maxVal < TRACK_MATCH_THRESHOLD) {
                        track.missedFrames++;
                        continue;
                    }

                    Rect candidate = new Rect(
                            searchRect.x + (int) mm.maxLoc.x,
                            searchRect.y + (int) mm.maxLoc.y,
                            track.templateGray.cols(),
                            track.templateGray.rows());
                    Rect blended = blendRect(track.boundingBox, candidate);
                    Point[] shiftedLandmarks = shiftLandmarks(track.landmarks, blended.x - track.boundingBox.x,
                            blended.y - track.boundingBox.y);

                    track.boundingBox = clampRect(blended, gray.cols(), gray.rows());
                    track.landmarks = shiftedLandmarks;
                    track.lastScore = mm.maxVal;
                    track.trackedOnly = true;
                    track.consecutiveTrackedOnlyFrames++;
                    track.missedFrames = 0;
                    refreshTemplate(track, gray);
                } finally {
                    result.release();
                    searchRegion.release();
                }
            }

            pruneLostTracks();
            return snapshot();
        } finally {
            gray.release();
        }
    }

    public synchronized boolean isEmpty() {
        return tracks.isEmpty();
    }

    public synchronized int size() {
        return tracks.size();
    }

    public synchronized double averageFaceAreaRatio(int frameWidth, int frameHeight) {
        if (tracks.isEmpty() || frameWidth <= 0 || frameHeight <= 0) {
            return 0.0;
        }

        double total = 0.0;
        double frameArea = (double) frameWidth * frameHeight;
        for (TrackState track : tracks) {
            total += track.boundingBox.area() / frameArea;
        }
        return total / tracks.size();
    }

    public synchronized void reset() {
        for (TrackState track : tracks) {
            track.templateGray.release();
        }
        tracks.clear();
        nextTrackId = 1;
    }

    @Override
    public synchronized void close() {
        reset();
    }

    private List<AssociationCandidate> buildCandidates(List<FaceDetection> detections) {
        List<AssociationCandidate> candidates = new ArrayList<>();
        for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++) {
            Rect trackRect = tracks.get(trackIndex).boundingBox;
            for (int detectionIndex = 0; detectionIndex < detections.size(); detectionIndex++) {
                Rect detectionRect = detections.get(detectionIndex).boundingBox();
                double iou = computeIoU(trackRect, detectionRect);
                if (iou < IOU_ASSOCIATION_THRESHOLD) {
                    continue;
                }

                double distancePenalty = normalizedCenterDistance(trackRect, detectionRect);
                double score = iou * 0.75 + (1.0 - distancePenalty) * 0.25;
                candidates.add(new AssociationCandidate(trackIndex, detectionIndex, score));
            }
        }
        return candidates;
    }

    private void createTrack(FaceDetection detection, Mat gray) {
        TrackState track = new TrackState(nextTrackId++, clampRect(detection.boundingBox(), gray.cols(), gray.rows()),
                detection.landmarksCopy(), detection.confidence(), false);
        refreshTemplate(track, gray);
        tracks.add(track);
    }

    private void updateTrackFromDetection(TrackState track, FaceDetection detection, Mat gray, boolean trackedOnly, double score) {
        track.boundingBox = clampRect(blendRect(track.boundingBox, detection.boundingBox()), gray.cols(), gray.rows());
        track.landmarks = detection.landmarksCopy();
        track.lastScore = Math.max(score, detection.confidence());
        track.trackedOnly = trackedOnly;
        if (!trackedOnly) {
            track.confirmationCount++;
            track.consecutiveTrackedOnlyFrames = 0;
        }
        track.missedFrames = 0;
        refreshTemplate(track, gray);
    }

    private void refreshTemplate(TrackState track, Mat gray) {
        Rect templateRect = clampRect(track.boundingBox, gray.cols(), gray.rows());
        if (templateRect.width < 8 || templateRect.height < 8) {
            return;
        }

        Mat newTemplate = null;
        try {
            newTemplate = new Mat(gray, templateRect).clone();
            track.templateGray.release();
            track.templateGray = newTemplate;
        } catch (Exception e) {
            // Ensure cleanup on exception
            if (newTemplate != null) {
                newTemplate.release();
            }
            throw e;
        }
    }

    private void pruneLostTracks() {
        tracks.removeIf(track -> {
            // Drop if missed too many frames
            if (track.missedFrames > MAX_MISSED_FRAMES) {
                track.templateGray.release();
                return true;
            }
            // Drop if it's been tracked-only for too long without being confirmed by a detector
            if (track.confirmationCount < 2 && track.consecutiveTrackedOnlyFrames > 3) {
                track.templateGray.release();
                return true;
            }
            // Drop unconfirmed tracks that are becoming unstable
            if (track.confirmationCount < 3 && track.consecutiveTrackedOnlyFrames > 8) {
                track.templateGray.release();
                return true;
            }
            return false;
        });
    }

    private List<TrackedFace> snapshot() {
        List<TrackedFace> snapshot = new ArrayList<>(tracks.size());
        for (TrackState track : tracks) {
            snapshot.add(new TrackedFace(track.id,
                    new Rect(track.boundingBox.x, track.boundingBox.y, track.boundingBox.width, track.boundingBox.height),
                    shiftLandmarks(track.landmarks, 0, 0), track.lastScore, track.trackedOnly));
        }
        snapshot.sort(Comparator.comparingInt(TrackedFace::id));
        return snapshot;
    }

    private static Mat toGray(Mat frame) {
        Mat gray = new Mat();
        if (frame.channels() == 1) {
            frame.copyTo(gray);
        } else if (frame.channels() == 4) {
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGRA2GRAY);
        } else {
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        }
        return gray;
    }

    private static Rect blendRect(Rect previous, Rect current) {
        int x = (int) Math.round(previous.x * RECT_SMOOTHING + current.x * (1.0 - RECT_SMOOTHING));
        int y = (int) Math.round(previous.y * RECT_SMOOTHING + current.y * (1.0 - RECT_SMOOTHING));
        int width = (int) Math.round(previous.width * RECT_SMOOTHING + current.width * (1.0 - RECT_SMOOTHING));
        int height = (int) Math.round(previous.height * RECT_SMOOTHING + current.height * (1.0 - RECT_SMOOTHING));
        return new Rect(Math.max(0, x), Math.max(0, y), Math.max(1, width), Math.max(1, height));
    }

    private static Rect expandRect(Rect rect, int maxWidth, int maxHeight, double expansionRatio) {
        int extraW = (int) Math.round(rect.width * expansionRatio);
        int extraH = (int) Math.round(rect.height * expansionRatio);
        int x = Math.max(0, rect.x - extraW);
        int y = Math.max(0, rect.y - extraH);
        int width = Math.min(maxWidth - x, rect.width + extraW * 2);
        int height = Math.min(maxHeight - y, rect.height + extraH * 2);
        return new Rect(x, y, Math.max(1, width), Math.max(1, height));
    }

    private static Rect clampRect(Rect rect, int maxWidth, int maxHeight) {
        int x = Math.max(0, Math.min(rect.x, Math.max(0, maxWidth - 1)));
        int y = Math.max(0, Math.min(rect.y, Math.max(0, maxHeight - 1)));
        int width = Math.max(1, Math.min(rect.width, maxWidth - x));
        int height = Math.max(1, Math.min(rect.height, maxHeight - y));
        return new Rect(x, y, width, height);
    }

    private static double computeIoU(Rect a, Rect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);

        int intersectionWidth = Math.max(0, x2 - x1);
        int intersectionHeight = Math.max(0, y2 - y1);
        double intersection = intersectionWidth * (double) intersectionHeight;
        double union = a.area() + b.area() - intersection;
        if (union <= 0.0) {
            return 0.0;
        }
        return intersection / union;
    }

    private static double normalizedCenterDistance(Rect a, Rect b) {
        double ax = a.x + a.width / 2.0;
        double ay = a.y + a.height / 2.0;
        double bx = b.x + b.width / 2.0;
        double by = b.y + b.height / 2.0;
        double distance = Math.hypot(ax - bx, ay - by);
        double diagonal = Math.hypot(Math.max(a.width, b.width), Math.max(a.height, b.height));
        return diagonal == 0.0 ? 1.0 : Math.min(1.0, distance / diagonal);
    }

    private static Point[] shiftLandmarks(Point[] source, int dx, int dy) {
        if (source == null || source.length == 0) {
            return new Point[0];
        }

        Point[] copy = new Point[source.length];
        for (int i = 0; i < source.length; i++) {
            Point point = source[i];
            copy[i] = point == null ? null : new Point(point.x + dx, point.y + dy);
        }
        return copy;
    }

    private record AssociationCandidate(int trackIndex, int detectionIndex, double score) {
    }

    private static final class TrackState {
        private final int id;
        private Rect boundingBox;
        private Point[] landmarks;
        private Mat templateGray = new Mat();
        private int missedFrames = 0;
        private int confirmationCount = 1;
        private int consecutiveTrackedOnlyFrames = 0;
        private double lastScore;
        private boolean trackedOnly;

        private TrackState(int id, Rect boundingBox, Point[] landmarks, double lastScore, boolean trackedOnly) {
            this.id = id;
            this.boundingBox = boundingBox;
            this.landmarks = landmarks;
            this.lastScore = lastScore;
            this.trackedOnly = trackedOnly;
        }
    }
}
