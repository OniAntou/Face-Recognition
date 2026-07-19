package com.example.facedetection.cli;

import com.example.facedetection.config.AppConfig;
import com.example.facedetection.config.ModelPaths;
import com.example.facedetection.service.FaceDetectorService;
import com.example.facedetection.util.MatUtils;
import com.example.facedetection.util.PathValidator;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Processes one image using the bundled SSD and gender models.
 *
 * <p>Usage: {@code FaceRecognitionCli <input-image> [output-image]}</p>
 */
public final class FaceRecognitionCli {

    private static final Logger logger = LoggerFactory.getLogger(FaceRecognitionCli.class);

    private FaceRecognitionCli() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            logger.error("Usage: FaceRecognitionCli <input-image> [output-image]");
            return;
        }

        OpenCV.loadLocally();
        ModelPaths models = ModelPaths.resolve();
        if (!models.hasSsd() || !models.hasGender()) {
            logger.error("Required SSD/gender models are unavailable: {}", models.describeMissingFiles());
            return;
        }

        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = args.length == 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : Path.of("Image_Output", "result.jpg").toAbsolutePath().normalize();

        if (!PathValidator.isValidImagePath(input.toString())
                || !Files.isRegularFile(input)
                || !PathValidator.isValidImagePath(output.toString())) {
            logger.error("Input/output path validation failed");
            return;
        }

        Mat image = null;
        FaceDetectorService service = null;
        try {
            Files.createDirectories(output.getParent());
            service = new FaceDetectorService(
                    models.ssdModel().toString(),
                    models.ssdConfig().toString(),
                    models.genderModel().toString(),
                    models.genderConfig().toString(),
                    AppConfig.getInstance().ssdConfidenceThreshold,
                    AppConfig.getInstance());

            image = Imgcodecs.imread(input.toString());
            if (image.empty()) {
                logger.error("Cannot read image at {}", input);
                return;
            }

            int count = service.detectAndDrawFaces(image);
            logger.info("{} face(s) detected", count);
            if (!Imgcodecs.imwrite(output.toString(), image)) {
                logger.error("Could not write image to {}", output);
                return;
            }
            logger.info("Output saved to {}", output);
        } catch (Exception e) {
            logger.error("Face recognition failed", e);
        } finally {
            MatUtils.safeRelease(image);
            if (service != null) {
                service.close();
            }
        }
    }
}
