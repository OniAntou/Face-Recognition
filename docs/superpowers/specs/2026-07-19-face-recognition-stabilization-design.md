# Face Recognition Stabilization and Release Hardening

**Date:** 2026-07-19
**Status:** Approved direction; implementation pending spec review
**Scope:** Runtime correctness, test reliability, updater security, installer consistency, and project documentation

## 1. Objective

Make the Face Recognition application reliable in both a development checkout and the packaged Windows installer. The work must address the defects found during the full-project review, produce evidence through automated tests/builds, and leave the documentation consistent with the actual repository.

The implementation will preserve the existing JavaFX/OpenCV/ONNX architecture. It will improve the boundaries between path resolution, model loading, camera capture, background processing, and update verification without replacing the application wholesale.

## 2. Findings that drive the design

- The bundled models are stored below `data/models/...`, while several tests and startup paths still expect flat files such as `data/deploy.prototxt`.
- The active YOLO detector uses ONNX Runtime `1.17.1`, but the bundled YOLO model uses ONNX IR version 10; the current runtime rejects it before inference.
- Two model-dependent tests currently error because of the stale paths. The integration test can also pass vacuously when no detector is created, and one assertion is tautological.
- `SecurityService.verifyDigitalSignature` currently accepts any existing `.exe`, and `UpdateService` treats an invalid signature as a warning. The updater therefore does not fail closed.
- Gender recognition is optional at startup but the frame path can still dereference a missing service when the option is enabled.
- JavaFX controls are read from an AI executor, selected-image processing runs synchronously on the JavaFX thread, and adaptive exposure is configured but never invoked from the capture loop.
- `MatPool` can release an empty borrowed matrix without returning it to its pool, and camera shutdown can release native resources while the capture thread is still alive.
- Detector/tracker thresholds are duplicated in code instead of consistently using `application.properties`.
- Version strings and installer/model packaging are inconsistent, and the installer build script contains a machine-specific `JAVA_HOME` and force-kills the application.
- `README.md` and `docs/PROJECT_SUMMARY.md` contain stale line counts, test counts, dates, and production-readiness claims.

## 3. Design

### 3.1 Model and resource path boundary

Add a `ModelPaths` value object/resolver responsible for locating the data directory and required model files. Resolution will support:

1. a development checkout containing `data/`;
2. a packaged app containing `app/data/`;
3. the directory relative to the running JAR/code source.

The resolver will return explicit paths for YOLO, SSD, Haar, and gender assets and will expose a validation result with the missing filenames. Startup, tests, the CLI, and installer documentation will use this boundary instead of constructing ad-hoc relative paths. Missing required files will produce an actionable error; optional detector assets will be reported as unavailable rather than causing a null dereference.

The installer will package every model that the application advertises, including the Haar cascade.

### 3.2 Detection runtime and configuration

Upgrade ONNX Runtime to a version verified against the bundled model's IR version. The implementation must prove that the actual `data/models/face/yolov8n-face.onnx` opens in the resulting runtime; changing only the Maven version without this smoke check is not sufficient.

The active `YoloFaceDetector` remains the YOLO implementation. Its startup path will:

- validate the model path before creating a session;
- report the runtime/model compatibility error with the model path and runtime version;
- validate the input tensor shape and output shape before inference;
- close partially initialized ONNX resources on failure.

`FaceTracker` and the OpenCV-based gender/SSD service will receive `AppConfig` values through constructors. Hard-coded thresholds and sizes that duplicate configuration will be removed. Configuration properties that are not consumed by the application will either be wired to a real behavior or removed, so the properties file does not promise controls that do nothing.

The duplicate YOLO helper will be reduced to a compatibility wrapper or removed after its tests are moved to the active detector. Unreferenced result types will be removed only when a repository-wide reference check confirms that they are dead.

### 3.3 Camera and processing lifecycle

`CameraManager` will own capture-loop exposure tuning. It will invoke adaptive exposure at the configured cadence on the capture thread, using the frame already owned by that loop, so no concurrent `VideoCapture.read()` calls are introduced. The existing enable/disable preference will be preserved.

Shutdown will request capture termination, wait for the capture thread to finish within the configured timeout, and only then release native camera resources. If the timeout expires, the service will log the live-thread state and avoid pretending that cleanup completed.

`MatPool` will always return matrices borrowed from one of its pools, including empty matrices; unpooled matrices will be released as a fallback. Pool tests will cover the empty-matrix path and active-object accounting.

### 3.4 JavaFX and background work

The controller will snapshot user options into thread-safe state before dispatching AI work. Background workers will operate only on immutable frame data and primitive option snapshots; they will not query JavaFX controls.

Selected-image processing will move to the existing AI executor. Native `Mat` ownership will be enclosed in `try/finally`, and all UI updates and error messages will return to the JavaFX application thread. The gender checkbox will be disabled or clearly marked unavailable when the gender service did not initialize, and the frame processor will still guard the service defensively.

### 3.5 Updater security boundary

The release parser will select the executable asset and its matching SHA-256 checksum asset. A release without a checksum asset will not be installable. The checksum value will be stored together with the URL selected during the update check; a caller-supplied URL must still match the checked URL.

`SecurityService` will provide:

- strict SHA-256 comparison using normalized hexadecimal values;
- a real Windows Authenticode verification path using a non-interactive PowerShell verifier, checking `Get-AuthenticodeSignature` status `Valid`;
- an optional configured signer identity check when a trusted subject/thumbprint is supplied;
- an explicit unsupported-platform/verification-error result rather than an optimistic success.

`UpdateService.verifyDownloadedFile` will fail if the file is missing, unexpectedly small, the checksum is absent/mismatched, or Authenticode verification is not valid. Only after all checks pass may the installer be launched. Download and verification tests will use injectable verification components or local fixtures and will never launch an installer.

### 3.6 Versioning and installer build

Use the Maven project version as the release version source. The packaged UI and Inno Setup script will consume the same version during the build. The installer script will no longer contain an unrelated hard-coded version.

`build_installer.bat` will:

- use an existing valid `JAVA_HOME`, otherwise resolve Java from `PATH`;
- fail with a clear message when `jpackage` or Inno Setup is unavailable;
- stop deleting/force-killing a running user process automatically;
- include `--enable-native-access=ALL-UNNAMED` in the packaged JVM options;
- retain only scoped build-output cleanup.

### 3.7 Tests, CI, and documentation

Tests will use `ModelPaths` and the real nested model layout. The suite will add coverage for:

- model path resolution and required-file validation;
- actual YOLO and SSD model initialization where the native runtime is available;
- detector configuration propagation;
- optional gender behavior;
- empty `MatPool` returns;
- path-boundary validation;
- updater checksum/signature fail-closed behavior.

Camera tests will remain hardware-aware: on a machine without camera index 0 they may be skipped with an explicit reason, but they must not be reported as unexplained errors. The vacuous integration assertion will be replaced with assertions about detector availability and result invariants.

Add a Windows GitHub Actions workflow that runs the Maven test/build checks without requiring a physical camera. Update `README.md` and `docs/PROJECT_SUMMARY.md` after the final test run so counts, paths, architecture, version, and known hardware limitations match the repository. Review all existing project documents for outdated claims and remove or rewrite only claims disproved by the current implementation.

## 4. Work breakdown and order

The implementation is divided into three independently verifiable subprojects:

1. **Runtime correctness:** path resolver, ONNX runtime/model loading, configuration injection, camera lifecycle/exposure, JavaFX background processing, optional gender handling, and `MatPool`.
2. **Security and release:** updater verification, version propagation, installer packaging, build script, and native-access options.
3. **Quality and handoff:** tests, CI, duplicate-code cleanup, final documentation review, and release/build verification.

Each subproject will be tested before the next begins. If a model/runtime dependency cannot be resolved from the available Maven artifacts, the implementation will stop at that specific compatibility decision rather than silently shipping a detector that cannot load its bundled model.

## 5. Acceptance criteria

- `mvn clean test` completes with zero failures and zero errors on the development machine; camera-only tests either pass or explicitly skip for missing hardware.
- The actual bundled YOLO model opens and runs through the active detector with the selected ONNX Runtime version.
- Startup and CLI resolve the same model layout used by the installer.
- A missing optional gender model cannot crash frame processing.
- Background processing does not read JavaFX controls or block selected-image UI processing.
- Empty matrices borrowed from `MatPool` are returned without increasing active counts.
- Invalid, unsigned, checksum-less, or checksum-mismatched installers are rejected by the updater.
- The installer script no longer depends on `C:\Users\USER\.jdk\jdk-25`, force-kills the app, or omits an advertised model.
- Project documents describe the current version, tests, paths, limitations, and build workflow accurately.
- The final repository diff contains only intentional source, test, build, CI, and documentation changes.

## 6. Out of scope

- Replacing JavaFX, OpenCV, ONNX Runtime, or the detector architecture with another stack.
- Automatically operating the user's camera, GUI, or installer during development verification.
- Adding a cloud backend, telemetry, or new recognition features.
- Re-exporting the supplied model unless a compatible ONNX Runtime cannot be obtained and the user explicitly approves that alternative.
