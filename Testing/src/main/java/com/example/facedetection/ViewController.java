package com.example.facedetection;

import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayInputStream;
import java.io.File;

public class ViewController {

    @FXML
    private ImageView originalImageView;

    @FXML
    private ImageView processedImageView;

    private CascadeClassifier faceDetector;

    public void initialize() {
        // Load the face detector
        String cascadePath = "data" + File.separator + "haarcascade_frontalface_default.xml";
        faceDetector = new CascadeClassifier();
        if (!faceDetector.load(cascadePath)) {
            System.err.println("ERROR: Cannot load cascade file at " + cascadePath);
        }
    }

    @FXML
    private void selectImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif"));
        File selectedFile = fileChooser.showOpenDialog(originalImageView.getScene().getWindow());
        if (selectedFile != null) {
            // Load the image
            Mat image = Imgcodecs.imread(selectedFile.getAbsolutePath());
            if (image.empty()) {
                System.err.println("ERROR: Cannot read image at " + selectedFile.getAbsolutePath());
                return;
            }

            // Display the original image
            originalImageView.setImage(mat2Image(image));

            // Perform face detection
            Mat processedImage = detectFaces(image);

            // Display the processed image
            processedImageView.setImage(mat2Image(processedImage));
        }
    }

    private Mat detectFaces(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        MatOfRect detections = new MatOfRect();
        faceDetector.detectMultiScale(
                gray,
                detections,
                1.1,
                5,
                0,
                new Size(30, 30),
                new Size()
        );

        Rect[] facesArray = detections.toArray();
        if (facesArray.length > 0) {
            for (Rect r : facesArray) {
                Imgproc.rectangle(
                        image,
                        new Point(r.x, r.y),
                        new Point(r.x + r.width, r.y + r.height),
                        new Scalar(0, 255, 0),
                        2
                );
            }
        }
        return image;
    }

    private Image mat2Image(Mat frame) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", frame, buffer);
        return new Image(new ByteArrayInputStream(buffer.toArray()));
    }
} 