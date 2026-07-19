# Face Recognition Project Summary

**Updated:** 2026-07-19
**Release version:** 1.2.3
**Status:** Runtime, release-hardening, and UI presentation changes implemented; signed installer packaging remains a separate operator step.

## What was fixed

### Runtime and models

- Added `ModelPaths` for the checkout layout and packaged `app/data` layout.
- Updated ONNX Runtime from 1.17.1 to 1.20.0 and verified the bundled YOLO model opens and runs.
- Repaired SSD/gender test fixtures and integration tests so they use the real nested model directories.
- Injected configured detector/tracker thresholds and input statistics instead of duplicating them in code.
- Made gender recognition safe when its model is unavailable, added a 70% confidence gate, validated landmark alignment, reduced background in gender crops, refreshed predictions periodically, and stabilized labels with a rolling vote window.
- Moved selected-image processing to the AI executor and stopped reading JavaFX controls from that executor.
- Connected adaptive exposure to the camera capture loop and improved camera shutdown/error cleanup.
- Corrected `MatPool` ownership and empty-matrix return behavior.
- Replaced unsafe directory string-prefix checks with normalized path-boundary checks.
- Redesigned the JavaFX presentation as a Photo Lab Desk UI with a warm light workspace, dark monitor viewports, flat controls, sentence-case English copy, and focused checkbox styling that keeps selection/focus inside the control box.

### Security and release

- Updater release parsing now requires an executable and a matching SHA-256 asset.
- Download verification always requires a valid checksum; Authenticode verification is enforced when `update.require.authenticode=true`.
- Authenticode verification uses non-interactive Windows PowerShell and supports optional trusted signer constraints when enabled.
- The current personal build sets `update.require.authenticode=false` so a checksum-verified unsigned installer can be used without an additional signing tool or certificate.
- Version `1.2.3` is shared by Maven, filtered FXML resources, jpackage, and the installer build scripts.
- The `v1.2.3` release can use the Windows IExpress fallback with a matching SHA-256 asset; Authenticode remains optional for this personal build.
- Installer packaging includes the Haar cascade, no longer force-kills the application, and no longer depends on a user-specific JDK path.
- Packaged/test JVMs enable native access explicitly.

### Code quality and documentation

- Removed the unused duplicate `YoloFaceService` implementation and unused `DetectorResult` record after reference checks.
- Added model, updater, pool, and path tests.
- Added Windows GitHub Actions Maven verification.
- Rewrote the README and this summary to remove stale counts, dates, paths, and production claims.

## Current architecture

```text
ViewController
|-- CameraManager
|-- FrameProcessor
|-- DetectionPipeline
|   |-- YoloFaceDetector
|   |-- SsdFaceDetector
|   |-- HaarFaceDetector
|   `-- FaceTracker
|-- FaceDetectorService (SSD + optional gender)
|-- UIManager
|-- ModelPaths
`-- AppConfig
```

## Verification snapshot

Command:

```text
mvn clean test
```

Result on 2026-07-19:

- 78 tests executed.
- 0 failures and 0 errors.
- 4 camera tests skipped because camera index 0 is unavailable on the verification machine.
- The real bundled YOLO model initialized successfully with ONNX Runtime 1.20.0.

The camera skips are hardware limitations, not test errors. A physical-camera verification pass is still required before claiming camera behavior for a particular device.

## Build commands

```text
mvn clean test
mvn clean verify
mvn clean package -DskipTests
```

For a Windows installer, close the application and run `build_installer.bat` from the project root. The script requires a JDK containing `jpackage`; it prefers Inno Setup 6 and otherwise uses the Windows IExpress fallback.

## Known operational requirements

- The updater intentionally rejects releases without checksum assets. Authenticode failures are also rejected when `update.require.authenticode=true`.
- Camera integration requires a real camera device.
- The packaged installer must be compiled and manually exercised by the release operator; this code review did not launch the GUI, camera, or installer automatically.
