package com.example.facedetection.cli;

import com.example.facedetection.service.FaceDetectorService;
import com.example.facedetection.util.PathValidator;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Command-line interface for face recognition.
 * Processes a single image and outputs the result with face detection annotations.
 */
public class FaceRecognitionCli {

    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionCli.class);

    public static void main(String[] args) {
        // 1. Load OpenCV native lib
        OpenCV.loadShared();
        logger.info("OpenCV loaded successfully");

        // 2. Build paths relative to project root
        String modelPath       = "data" + File.separator + "res10_300x300_ssd_iter_140000.caffemodel";
        String configPath      = "data" + File.separator + "deploy.prototxt";
        String genderModelPath = "data" + File.separator + "gender_net.caffemodel";
        String genderConfigPath = "data" + File.separator + "gender_deploy.prototxt";
        String inPath  = "Image_Test" + File.separator + "input.jpg";
        String outPath = "Image_Output" + File.separator + "result.jpg";

        // 3. Validate all paths to prevent path traversal attacks
        if (!validatePaths(modelPath, configPath, genderModelPath, genderConfigPath, inPath, outPath)) {
            logger.error("Path validation failed. Exiting.");
            System.exit(1);
        }

        Mat image = null;
        try {
            // 4. Initialize Service
            FaceDetectorService service = new FaceDetectorService(
                    modelPath, configPath, genderModelPath, genderConfigPath);

            // 5. Read input image
            image = Imgcodecs.imread(inPath);
            if (image.empty()) {
                logger.error("Cannot read image at {}", inPath);
                return;
            }

            // 6. Detect and draw faces
            int count = service.detectAndDrawFaces(image);
            if (count == 0) {
                logger.info("No faces detected.");
            } else {
                logger.info("{} face(s) detected.", count);
            }

            // 7. Save result
            File outputDir = new File("Image_Output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            if (Imgcodecs.imwrite(outPath, image)) {
                logger.info("Output saved to {}", outPath);
            } else {
                logger.error("Could not write image to {}", outPath);
            }
        } catch (Exception e) {
            logger.error("Error during face recognition: {}", e.getMessage(), e);
        } finally {
            // Ensure native memory is always released
            if (image != null) {
                image.release();
            }
        }
    }

    /**
     * Validates all file paths to prevent path traversal attacks.
     *
     * @param paths paths to validate
     * @return true if all paths are valid
     */
    private static boolean validatePaths(String... paths) {
        for (String path : paths) {
            if (!PathValidator.isValidPath(path)) {
                logger.error("Invalid path detected: {}", path);
                return false;
            }
        }
        return true;
    }
}
