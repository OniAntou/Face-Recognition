import java.io.File;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

public class FaceRecognition {
    public static void main(String[] args) {
        // 1. Load OpenCV native lib
        OpenCV.loadShared();
        System.out.println("OpenCV loaded successfully");

        // 2. Build paths relative to project root
        String cascadePath = "data" + File.separator + "haarcascade_frontalface_default.xml";
        String inPath      = "Image_Test" + File.separator + "input.jpg";
        String outPath     = "Image_Output" + File.separator + "result.jpg";

        // 3. Load cascade classifier
        CascadeClassifier faceDetector = new CascadeClassifier();
        if (!faceDetector.load(cascadePath)) {
            System.err.println("ERROR: Cannot load cascade file at " + cascadePath);
            return;
        }

        // 4. Read input image
        Mat image = Imgcodecs.imread(inPath);
        if (image.empty()) {
            System.err.println("ERROR: Cannot read image at " + inPath);
            return;
        }

        // 5. Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // 6. Detect faces
        MatOfRect detections = new MatOfRect();
        faceDetector.detectMultiScale(
            gray, 
            detections, 
            1.1,       // scale factor
            5,         // min neighbors
            0, 
            new Size(30, 30), 
            new Size()
        );

        // 7. Draw rectangles
        Rect[] facesArray = detections.toArray();
        if (facesArray.length == 0) {
            System.out.println("No faces detected.");
        } else {
            for (Rect r : facesArray) {
                Imgproc.rectangle(
                    image,
                    new Point(r.x, r.y),
                    new Point(r.x + r.width, r.y + r.height),
                    new Scalar(0, 255, 0),
                    2
                );
            }
            System.out.println(facesArray.length + " face(s) detected.");
        }

        // 8. Save result
        if (Imgcodecs.imwrite(outPath, image)) {
            System.out.println("Output saved to " + outPath);
        } else {
            System.err.println("ERROR: Could not write image to " + outPath);
        }
    }
}