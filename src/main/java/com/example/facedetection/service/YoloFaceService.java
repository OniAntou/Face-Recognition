package com.example.facedetection.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect2d;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * YOLOv8-face detector with optional landmark extraction for face alignment.
 */
public class YoloFaceService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(YoloFaceService.class);
    private static final String ENGINE_LABEL = "YOLOv8";

    private final OrtEnvironment env;
    private final OrtSession session;
    private final float confThreshold;
    private final float nmsThreshold;
    private final Size inputSize = new Size(640, 640);

    public YoloFaceService(String modelPath) throws OrtException {
        this(modelPath, 0.25f, 0.45f);
    }

    public YoloFaceService(String modelPath, float confThreshold, float nmsThreshold) throws OrtException {
        this.confThreshold = confThreshold;
        this.nmsThreshold = nmsThreshold;
        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setInterOpNumThreads(1);
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        logger.info("ONNX Runtime: Forced CPU execution for stability.");

        this.session = env.createSession(modelPath, options);
        logger.info("YOLOv8-face session initialized.");
    }

    public Rect[] detectFaces(Mat frame) {
        List<FaceDetection> detections = detectDetections(frame);
        Rect[] rects = new Rect[detections.size()];
        for (int i = 0; i < detections.size(); i++) {
            rects[i] = detections.get(i).boundingBox();
        }
        return rects;
    }

    public List<FaceDetection> detectDetections(Mat frame) {
        if (frame == null || frame.empty()) {
            return List.of();
        }

        Mat blob = null;
        try {
            blob = Dnn.blobFromImage(frame, 1.0 / 255.0, inputSize, new Scalar(0, 0, 0), true, false);

            float[] chwData = new float[(int) blob.total()];
            Mat flat = blob.reshape(1, 1);
            flat.get(0, 0, chwData);
            flat.release();

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chwData), new long[]{1, 3, 640, 640});
            try (OrtSession.Result results = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor))) {
                OnnxValue outputValue = results.get(0);
                float[][] data = extractTensorData(outputValue);
                if (data == null) {
                    logger.error("Could not extract float data from ONNX output");
                    return List.of();
                }
                return processOutput(data, frame.cols(), frame.rows());
            } finally {
                inputTensor.close();
            }
        } catch (Exception e) {
            logger.error("Inference error", e);
            return List.of();
        } finally {
            if (blob != null) {
                blob.release();
            }
        }
    }

    private float[][] extractTensorData(OnnxValue outputValue) throws OrtException {
        if (!(outputValue instanceof OnnxTensor tensor)) {
            return null;
        }

        Object value = tensor.getValue();
        if (value instanceof float[][][] triple) {
            return triple[0];
        }
        if (value instanceof float[][] dual) {
            return dual;
        }
        return null;
    }

    private List<FaceDetection> processOutput(float[][] data, int imgWidth, int imgHeight) {
        if (data == null || data.length == 0) {
            return List.of();
        }

        boolean transposed = data.length > 8000;
        int numPredictions = transposed ? data.length : data[0].length;
        int numChannels = transposed ? data[0].length : data.length;

        List<Rect2d> boxes = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        List<Point[]> landmarkSets = new ArrayList<>();

        float xFactor = (float) imgWidth / 640f;
        float yFactor = (float) imgHeight / 640f;

        for (int i = 0; i < numPredictions; i++) {
            float confidence = transposed ? data[i][4] : data[4][i];
            if (confidence <= confThreshold) {
                continue;
            }

            float x = transposed ? data[i][0] : data[0][i];
            float y = transposed ? data[i][1] : data[1][i];
            float w = transposed ? data[i][2] : data[2][i];
            float h = transposed ? data[i][3] : data[3][i];

            float actualXFactor = x <= 1.01f ? imgWidth : xFactor;
            float actualYFactor = y <= 1.01f ? imgHeight : yFactor;

            int left = (int) ((x - 0.5 * w) * actualXFactor);
            int top = (int) ((y - 0.5 * h) * actualYFactor);
            int width = (int) (w * actualXFactor);
            int height = (int) (h * actualYFactor);
            Rect2d box = new Rect2d(left, top, width, height);

            boxes.add(box);
            confidences.add(confidence);
            landmarkSets.add(extractLandmarks(data, transposed, i, numChannels, actualXFactor, actualYFactor));
        }

        if (boxes.isEmpty()) {
            return List.of();
        }

        MatOfRect2d boxesMat = new MatOfRect2d();
        MatOfFloat confidencesMat = new MatOfFloat();
        MatOfInt indices = new MatOfInt();
        try {
            boxesMat.fromList(boxes);
            confidencesMat.fromList(confidences);
            Dnn.NMSBoxes(boxesMat, confidencesMat, confThreshold, nmsThreshold, indices);

            int[] selected = indices.toArray();
            List<FaceDetection> results = new ArrayList<>(selected.length);
            for (int index : selected) {
                Rect2d box = boxes.get(index);
                Rect rect = clampRect(new Rect((int) box.x, (int) box.y, (int) box.width, (int) box.height), imgWidth, imgHeight);
                results.add(new FaceDetection(rect, landmarkSets.get(index), confidences.get(index), ENGINE_LABEL));
            }
            return results;
        } finally {
            boxesMat.release();
            confidencesMat.release();
            indices.release();
        }
    }

    static Point[] extractLandmarks(float[][] data, boolean transposed, int predictionIndex,
                                    int numChannels, float xFactor, float yFactor) {
        if (numChannels < 15) {
            return new Point[0];
        }

        Point[] landmarks = new Point[5];
        for (int landmarkIndex = 0; landmarkIndex < 5; landmarkIndex++) {
            int xChannel = 5 + landmarkIndex * 2;
            int yChannel = xChannel + 1;
            float x = transposed ? data[predictionIndex][xChannel] : data[xChannel][predictionIndex];
            float y = transposed ? data[predictionIndex][yChannel] : data[yChannel][predictionIndex];
            landmarks[landmarkIndex] = new Point(x * xFactor, y * yFactor);
        }
        return landmarks;
    }

    private static Rect clampRect(Rect rect, int imgWidth, int imgHeight) {
        int x = Math.max(0, Math.min(rect.x, Math.max(0, imgWidth - 1)));
        int y = Math.max(0, Math.min(rect.y, Math.max(0, imgHeight - 1)));
        int width = Math.max(1, Math.min(rect.width, imgWidth - x));
        int height = Math.max(1, Math.min(rect.height, imgHeight - y));
        return new Rect(x, y, width, height);
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            logger.error("Error closing ONNX resources", e);
        }
    }
}
