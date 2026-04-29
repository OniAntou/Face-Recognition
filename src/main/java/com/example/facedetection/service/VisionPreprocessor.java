package com.example.facedetection.service;

import com.example.facedetection.config.AppConfig;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared preprocessing utilities for face detection in challenging lighting.
 * The goal is to preserve facial detail when highlights are clipped or uneven.
 * Uses configuration from AppConfig instead of hard-coded constants.
 */
public final class VisionPreprocessor {

    private final AppConfig config;

    public VisionPreprocessor(AppConfig config) {
        this.config = config;
    }

    public VisionPreprocessor() {
        this(AppConfig.getInstance());
    }

    public double estimateBrightness(Mat source) {
        if (source == null || source.empty()) {
            return 0.0;
        }

        Mat gray = new Mat();
        Mat bgr = ensureBgr(source);
        try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            return Core.mean(gray).val[0];
        } finally {
            gray.release();
            if (bgr != source) {
                bgr.release();
            }
        }
    }

    public boolean isLikelyOverexposed(Mat source) {
        if (source == null || source.empty()) {
            return false;
        }

        Mat gray = new Mat();
        Mat highlightMask = new Mat();
        Mat bgr = ensureBgr(source);

        try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            double meanBrightness = Core.mean(gray).val[0];
            Imgproc.threshold(gray, highlightMask, 245, 255, Imgproc.THRESH_BINARY);
            double highlightRatio = Core.countNonZero(highlightMask) / (double) (gray.rows() * gray.cols());
            return meanBrightness >= config.brightnessThreshold || highlightRatio >= config.highlightRatioThreshold;
        } finally {
            gray.release();
            highlightMask.release();
            if (bgr != source) {
                bgr.release();
            }
        }
    }

    public Mat prepareForFaceDetection(Mat source) {
        if (source == null || source.empty()) {
            return new Mat();
        }

        Mat bgr = ensureBgr(source);
        Mat normalized = bgr.clone();
        Mat lab = new Mat();
        Mat enhancedLightness = new Mat();
        List<Mat> labChannels = new ArrayList<>(3);
        boolean overexposed = isLikelyOverexposed(bgr);

        try {
            Imgproc.cvtColor(normalized, lab, Imgproc.COLOR_BGR2Lab);
            Core.split(lab, labChannels);

            Mat lightness = labChannels.get(0);
            CLAHE clahe = Imgproc.createCLAHE(overexposed ? 3.2 : 2.0, new Size(8, 8));
            clahe.apply(lightness, enhancedLightness);
            clahe.collectGarbage();

            applyGamma(enhancedLightness, enhancedLightness, overexposed ? config.aggressiveGamma : config.mildGamma);

            Mat oldLightness = labChannels.set(0, enhancedLightness);
            if (oldLightness != null && oldLightness != enhancedLightness) {
                oldLightness.release();
            }

            Core.merge(labChannels, lab);
            Imgproc.cvtColor(lab, normalized, Imgproc.COLOR_Lab2BGR);
            return normalized;
        } catch (Exception e) {
            normalized.release();
            return bgr == source ? source.clone() : bgr.clone();
        } finally {
            for (Mat channel : labChannels) {
                if (channel != null && channel != enhancedLightness) {
                    channel.release();
                }
            }
            if (enhancedLightness != null && enhancedLightness != normalized && !enhancedLightness.empty()) {
                enhancedLightness.release();
            }
            lab.release();
            if (bgr != source) {
                bgr.release();
            }
        }
    }

    private Mat ensureBgr(Mat source) {
        if (source.channels() == 3) {
            return source;
        }

        Mat converted = new Mat();
        if (source.channels() == 4) {
            Imgproc.cvtColor(source, converted, Imgproc.COLOR_BGRA2BGR);
        } else if (source.channels() == 1) {
            Imgproc.cvtColor(source, converted, Imgproc.COLOR_GRAY2BGR);
        } else {
            source.copyTo(converted);
        }
        return converted;
    }

    private void applyGamma(Mat source, Mat destination, double gamma) {
        Mat lut = new Mat(1, 256, CvType.CV_8UC1);
        try {
            byte[] table = new byte[256];
            for (int i = 0; i < table.length; i++) {
                int corrected = (int) Math.round(Math.pow(i / 255.0, gamma) * 255.0);
                table[i] = (byte) Math.max(0, Math.min(255, corrected));
            }
            lut.put(0, 0, table);
            Core.LUT(source, lut, destination);
        } finally {
            lut.release();
        }
    }
}
