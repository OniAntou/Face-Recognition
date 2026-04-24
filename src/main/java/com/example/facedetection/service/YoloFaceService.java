package com.example.facedetection.service;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for YOLOv8-face detection using ONNX.
 */
public class YoloFaceService {
    private static final Logger logger = LoggerFactory.getLogger(YoloFaceService.class);
    
    private final Net net;
    private final float confThreshold;
    private final float nmsThreshold;
    private final Size inputSize = new Size(640, 640);

    public YoloFaceService(String modelPath) {
        this(modelPath, 0.45f, 0.45f);
    }

    public YoloFaceService(String modelPath, float confThreshold, float nmsThreshold) {
        this.confThreshold = confThreshold;
        this.nmsThreshold = nmsThreshold;
        this.net = Dnn.readNetFromONNX(modelPath);
        
        // Hardware Acceleration
        this.net.setPreferableBackend(Dnn.DNN_BACKEND_DEFAULT);
        this.net.setPreferableTarget(Dnn.DNN_TARGET_OPENCL);
        
        if (net.empty()) {
            throw new RuntimeException("Could not load YOLO ONNX model from " + modelPath);
        }
        logger.info("YOLOv8-face ONNX model loaded.");
    }

    public Rect[] detectFaces(Mat frame) {
        Mat blob = Dnn.blobFromImage(frame, 1.0 / 255.0, inputSize, new Scalar(0, 0, 0), true, false);
        net.setInput(blob);
        
        List<Mat> outputs = new ArrayList<>();
        net.forward(outputs, net.getUnconnectedOutLayersNames());
        
        Mat output = outputs.get(0); // [1, 5, 8400] or similar
        // Reshape and Transpose for YOLOv8
        output = output.reshape(1, output.size(1));
        Core.transpose(output, output);

        List<Rect2d> boxes = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();

        float xFactor = (float) (frame.cols() / inputSize.width);
        float yFactor = (float) (frame.rows() / inputSize.height);

        for (int i = 0; i < output.rows(); i++) {
            float confidence = (float) output.get(i, 4)[0];
            if (confidence > confThreshold) {
                float x = (float) output.get(i, 0)[0];
                float y = (float) output.get(i, 1)[0];
                float w = (float) output.get(i, 2)[0];
                float h = (float) output.get(i, 3)[0];

                int left = (int) ((x - 0.5 * w) * xFactor);
                int top = (int) ((y - 0.5 * h) * yFactor);
                int width = (int) (w * xFactor);
                int height = (int) (h * yFactor);

                boxes.add(new Rect2d(left, top, width, height));
                confidences.add(confidence);
            }
        }

        Rect[] results = new Rect[0];
        if (!boxes.isEmpty()) {
            MatOfRect2d boxesMat = new MatOfRect2d();
            boxesMat.fromList(boxes);
            MatOfFloat confsMat = new MatOfFloat();
            confsMat.fromList(confidences);
            MatOfInt indices = new MatOfInt();

            Dnn.NMSBoxes(boxesMat, confsMat, confThreshold, nmsThreshold, indices);

            int[] idxArray = indices.toArray();
            results = new Rect[idxArray.length];
            for (int i = 0; i < idxArray.length; i++) {
                Rect2d box = boxes.get(idxArray[i]);
                results[i] = new Rect((int)box.x, (int)box.y, (int)box.width, (int)box.height);
            }
            
            boxesMat.release();
            confsMat.release();
            indices.release();
        }

        blob.release();
        for (Mat m : outputs) m.release();
        output.release();

        return results;
    }
}
