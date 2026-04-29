package com.example.facedetection.detector;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.example.facedetection.service.FaceDetection;
import com.example.facedetection.util.MatUtils;
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
 * YOLOv8-face detector implementing FaceDetector interface.
 * Provides fast face detection with optional landmark extraction.
 */
public class YoloFaceDetector implements FaceDetector {

    private static final Logger logger = LoggerFactory.getLogger(YoloFaceDetector.class);
    private static final String ENGINE_NAME = "YOLOv8";
    private static final Size INPUT_SIZE = new Size(640, 640);
    private static final int NUM_LANDMARKS = 5;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final float confidenceThreshold;
    private final float nmsThreshold;
    private final boolean available;

    /**
     * Creates a YOLO face detector.
     *
     * @param modelPath path to YOLOv8 ONNX model
     * @param confidenceThreshold minimum confidence (0.0 - 1.0)
     * @param nmsThreshold NMS IoU threshold
     * @throws OrtException if model loading fails
     */
    public YoloFaceDetector(String modelPath, float confidenceThreshold, float nmsThreshold)
            throws OrtException {
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;

        this.env = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setInterOpNumThreads(1);
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        this.session = env.createSession(modelPath, options);
        this.available = true;

        logger.info("{} detector initialized from {}", ENGINE_NAME, modelPath);
    }

    @Override
    public String getName() {
        return ENGINE_NAME;
    }

    @Override
    public List<FaceDetection> detect(Mat frame) {
        if (!available || frame == null || frame.empty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        Mat blob = null;

        try {
            blob = org.opencv.dnn.Dnn.blobFromImage(
                    frame, 1.0 / 255.0, INPUT_SIZE, new Scalar(0, 0, 0), true, false
            );

            float[] chwData = new float[(int) blob.total()];
            Mat flat = blob.reshape(1, 1);
            flat.get(0, 0, chwData);
            flat.release();

            OnnxTensor inputTensor = OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(chwData), new long[]{1, 3, 640, 640}
            );

            try (OrtSession.Result results = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), inputTensor)
            )) {
                OnnxValue outputValue = results.get(0);
                float[][] data = extractTensorData(outputValue);

                if (data == null) {
                    logger.error("Could not extract float data from ONNX output");
                    return Collections.emptyList();
                }

                List<FaceDetection> detections = processOutput(data, frame.cols(), frame.rows());
                long processingTime = System.currentTimeMillis() - startTime;

                logger.debug("{} detected {} faces in {} ms",
                        ENGINE_NAME, detections.size(), processingTime);

                return detections;
            } finally {
                inputTensor.close();
            }
        } catch (Exception e) {
            logger.error("{} inference error: {}", ENGINE_NAME, e.getMessage());
            return Collections.emptyList();
        } finally {
            MatUtils.safeRelease(blob);
        }
    }

    @Override
    public boolean isAvailable() {
        return available && session != null;
    }

    @Override
    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
                logger.info("{} session closed", ENGINE_NAME);
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            logger.error("Error closing {} resources: {}", ENGINE_NAME, e.getMessage());
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
            return Collections.emptyList();
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
            if (confidence <= confidenceThreshold) {
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
            return Collections.emptyList();
        }

        return applyNms(boxes, confidences, landmarkSets, imgWidth, imgHeight);
    }

    private List<FaceDetection> applyNms(List<Rect2d> boxes, List<Float> confidences,
                                          List<Point[]> landmarkSets, int imgWidth, int imgHeight) {
        MatOfRect2d boxesMat = new MatOfRect2d();
        MatOfFloat confidencesMat = new MatOfFloat();
        MatOfInt indices = new MatOfInt();

        try {
            boxesMat.fromList(boxes);
            confidencesMat.fromList(confidences);
            Dnn.NMSBoxes(boxesMat, confidencesMat, confidenceThreshold, nmsThreshold, indices);

            int[] selected = indices.toArray();
            List<FaceDetection> results = new ArrayList<>(selected.length);

            for (int index : selected) {
                Rect2d box = boxes.get(index);
                Rect rect = clampRect(
                        new Rect((int) box.x, (int) box.y, (int) box.width, (int) box.height),
                        imgWidth, imgHeight
                );
                results.add(new FaceDetection(
                        rect, landmarkSets.get(index), confidences.get(index), ENGINE_NAME
                ));
            }
            return results;
        } finally {
            MatUtils.safeRelease(boxesMat);
            MatUtils.safeRelease(confidencesMat);
            MatUtils.safeRelease(indices);
        }
    }

    private Point[] extractLandmarks(float[][] data, boolean transposed, int predictionIndex,
                                      int numChannels, float xFactor, float yFactor) {
        if (numChannels < 15) {
            return new Point[0];
        }

        Point[] landmarks = new Point[NUM_LANDMARKS];
        for (int landmarkIndex = 0; landmarkIndex < NUM_LANDMARKS; landmarkIndex++) {
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
}
