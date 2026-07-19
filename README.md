# Face Recognition & Analysis

Face detection and gender-classification desktop application built with Java 25, JavaFX, OpenCV, and ONNX Runtime. The current release version is **1.2.1**.

## Features

- YOLOv8-face detection with SSD ResNet-10 and Haar fallback engines.
- Optional Levi-Hassner gender classification with landmark-aware alignment.
- Bright-light preprocessing, adaptive camera exposure, tracking, and FPS metrics.
- Model-path resolution that works from a checkout and from the packaged Windows app.
- Fail-closed update verification using SHA-256 and Windows Authenticode.
- Windows installer build with bundled models and JRE.

## Requirements

| Tool | Version |
|------|---------|
| JDK | 25 |
| Maven | 3.9+ |
| Inno Setup | 6, only for the installer |

## Model layout

The application expects the bundled assets in this layout:

```text
data/
|-- haarcascade/haarcascade_frontalface_default.xml
`-- models/
    |-- face/
    |   |-- deploy.prototxt
    |   |-- res10_300x300_ssd_iter_140000.caffemodel
    |   `-- yolov8n-face.onnx
    `-- gender/
        |-- gender_deploy.prototxt
        `-- gender_net.caffemodel
```

`ModelPaths` resolves this layout from the project root, `app/data` in an app image, or the directory beside the running JAR.

## Quick start

```bash
mvn clean test
mvn javafx:run
```

The command-line image processor accepts an input image and an optional output path:

```bash
mvn -q package -DskipTests
java -cp "target/classes;target/dependency/*" com.example.facedetection.cli.FaceRecognitionCli input.jpg output.jpg
```

## Build the Windows installer

Run `build_installer.bat` from the repository root. The script reads the version from `pom.xml`, locates a JDK with `jpackage`, builds the app image, and invokes Inno Setup when it is installed.

The script does not terminate a running application automatically. Close Face Recognition before compiling an installer.

## Configuration

Edit [`src/main/resources/application.properties`](src/main/resources/application.properties) for camera, detection, tracker, preprocessing, and updater settings. Update verification always requires a checksum and a valid Authenticode signature; trusted signer subject/thumbprint constraints can be configured when the release certificate is known.

## Architecture

```text
ViewController
|-- CameraManager - capture lifecycle and adaptive exposure
|-- FrameProcessor - preprocessing, annotations, image conversion
|-- DetectionPipeline - YOLO -> SSD -> Haar fallback and tracking
|-- FaceDetectorService - SSD face net and optional gender net
|-- UIManager - JavaFX presentation updates
`-- ModelPaths/AppConfig - resources and configuration boundaries
```

## Tests and CI

Run the full suite with:

```bash
mvn clean test
mvn clean verify
```

The suite covers model loading, path resolution, detector/pipeline behavior, native matrix lifecycle, updater verification, and integration flows. Camera integration tests are hardware-aware and explicitly skip when camera index 0 is unavailable. GitHub Actions runs the Maven verification job on Windows with JDK 25.

## Security note

An update release must provide an executable asset and a matching SHA-256 checksum asset. The updater rejects missing or mismatched checksums, unsigned installers, invalid signatures, unsupported verification, and URLs that were not selected during the update check.

## License

MIT License - [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)
