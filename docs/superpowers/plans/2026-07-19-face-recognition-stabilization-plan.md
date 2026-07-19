# Face Recognition Stabilization Implementation Plan

**Spec:** `docs/superpowers/specs/2026-07-19-face-recognition-stabilization-design.md`
**Date:** 2026-07-19

## Execution order

### 1. Establish the failing baseline and shared test setup

Files:

- `pom.xml`
- `src/test/java/com/example/facedetection/...`

Actions:

1. Run `mvn clean test` and capture the current failures, skipped camera tests, Java version, and ONNX model-load error.
2. Update test OpenCV initialization to use the local OpenCV loader consistently.
3. Add a small test fixture/path helper under `src/test/java` only if production `ModelPaths` cannot be tested without duplicating path logic.

Verification: the baseline failure is recorded before source changes; no test is weakened to hide a real model error.

### 2. Runtime correctness

Files:

- `src/main/java/com/example/facedetection/config/ModelPaths.java` (new)
- `src/main/java/com/example/facedetection/config/AppConfig.java`
- `src/main/java/com/example/facedetection/controller/ViewController.java`
- `src/main/java/com/example/facedetection/processor/FrameProcessor.java`
- `src/main/java/com/example/facedetection/service/CameraManager.java`
- `src/main/java/com/example/facedetection/service/DetectionPipeline.java`
- `src/main/java/com/example/facedetection/service/FaceDetectorService.java`
- `src/main/java/com/example/facedetection/service/FaceTracker.java`
- `src/main/java/com/example/facedetection/detector/YoloFaceDetector.java`
- `src/main/java/com/example/facedetection/detector/SsdFaceDetector.java`
- `src/main/java/com/example/facedetection/util/MatPool.java`
- `src/main/java/com/example/facedetection/util/PathValidator.java`
- `src/main/java/com/example/facedetection/cli/FaceRecognitionCli.java`
- `pom.xml`

Actions:

1. Add `ModelPaths` resolution for checkout and packaged `app/data` layouts, with required/optional asset validation.
2. Upgrade ONNX Runtime only after verifying a candidate version against the bundled YOLO model; add a real model session smoke test.
3. Pass `AppConfig` into tracker and OpenCV detector code; remove duplicated hard-coded values and unused configuration claims.
4. Make optional gender recognition safe in both controller and frame processor.
5. Snapshot UI options before background work; move selected-image processing off the JavaFX thread and release all native matrices in `finally` blocks.
6. Invoke adaptive exposure from the camera capture loop at the configured cadence and make camera shutdown wait for the capture thread before releasing native resources.
7. Fix `MatPool` return behavior for empty and unpooled matrices and add active-count coverage.
8. Replace path-prefix checks with normalized `Path.startsWith` boundary checks.
9. Update CLI arguments and model resolution so it does not assume `Image_Test/input.jpg` or flat model files.

Verification after each cohesive change: run the focused unit/integration tests, then `mvn test`. Do not launch the GUI or camera.

### 3. Updater and release hardening

Files:

- `src/main/java/com/example/facedetection/service/SecurityService.java`
- `src/main/java/com/example/facedetection/service/UpdateService.java`
- `src/main/java/com/example/facedetection/config/AppConfig.java`
- `src/main/resources/application.properties`
- `pom.xml`
- `src/main/resources/com/example/facedetection/scene.fxml`
- `installer/modern_setup.iss`
- `build_installer.bat`

Actions:

1. Parse the executable and matching SHA-256 release assets and retain the checksum with the checked URL.
2. Implement strict checksum normalization/comparison and a real non-interactive Windows Authenticode verification adapter.
3. Make missing checksum, unsupported verifier, download mismatch, and invalid file type fail closed; make Authenticode rejection configurable for unsigned personal releases.
4. Inject verification dependencies where needed so tests never execute an installer.
5. Use the Maven project version for the UI, installer, and build output; remove stale version strings.
6. Remove hard-coded `JAVA_HOME`, force-kill behavior, and omitted Haar packaging from the installer workflow.
7. Add native-access JVM options to packaged execution.

Verification: focused security tests, `mvn clean package -DskipTests`, and static checks over installer/build files. No installer execution.

### 4. Tests, CI, cleanup, and documents

Files:

- `src/test/java/com/example/facedetection/...` (existing tests)
- new focused tests under `src/test/java/com/example/facedetection/config`, `.../security`, and `.../util` as needed
- `.github/workflows/ci.yml` (new)
- `src/main/java/com/example/facedetection/service/YoloFaceService.java`
- `src/main/java/com/example/facedetection/detector/DetectorResult.java`
- `README.md`
- `docs/PROJECT_SUMMARY.md`

Actions:

1. Replace stale flat-path fixtures and vacuous assertions with model-layout and result-invariant assertions.
2. Add model initialization, path resolution, updater fail-closed, and pool lifecycle coverage.
3. Remove only duplicate/dead classes confirmed unused after `rg` and compilation checks.
4. Add Windows CI for Maven tests/builds without physical-camera assumptions.
5. Update README and project summary with actual version, architecture, test results, build commands, and camera limitations.
6. Review every project document for outdated claims before finalizing; remove obsolete generated/duplicate documentation only when it is clearly superseded.

### 5. Final verification

Run:

```text
mvn clean test
mvn clean package -DskipTests
git diff --check
git status --short --branch
```

Also inspect the final diff for:

- no credentials or machine-specific paths;
- no automatic GUI/camera/installer launch;
- no tests that pass vacuously;
- no stale model paths or version claims in code/docs/installer;
- no documents left with obsolete test counts or production status.

The final report will separate verified results from hardware-dependent skips and will link the updated project documents.
