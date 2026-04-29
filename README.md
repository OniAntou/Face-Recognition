# Face Recognition & Analysis

Real-time face detection and gender classification application built with **Java 25**, **OpenCV**, and **JavaFX**.

Features a hybrid AI engine with **YOLOv8-face** (ONNX), **SSD ResNet-10** (Caffe), and **Haar Cascade** fallback, plus **Levi-Hassner** gender classification.

---

## Features

- **Multi-Engine Detection** — Pluggable architecture supporting YOLOv8, SSD, and Haar Cascade
- **Configurable** — 30+ parameters via `application.properties`
- **Gender Classification** — Levi-Hassner model with confidence scoring
- **Silent Updates** — Auto-detect and install updates from GitHub
- **Dark UI** — Professional interface with AtlantaFX theme
- **30+ FPS** — Real-time camera with adaptive exposure
- **Tested** — 21 automated tests (unit, integration, memory)
- **Installer** — Standalone Windows setup with bundled JRE

---

## Requirements

| Tool | Version |
|------|---------|
| JDK | 25 |
| Maven | 3.9+ |
| Inno Setup 6 | For `.exe` build |

---

## Quick Start

```bash
# Clone and run
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition
mvn javafx:run

# Run tests
mvn test

# Build installer
build_installer.bat  # Output: releases/FaceRecognition_Setup.exe
```

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Detection
yolo.confidence.threshold=0.65
ssd.confidence.threshold=0.60

# Camera
camera.frame.interval.ms=33
camera.exposure.target.brightness=165.0

# Preprocessing
vision.brightness.threshold=190.0
vision.aggressive.gamma=1.35
```

---

## Architecture

```
ViewController (250 lines)
    ├── UIManager          (UI coordination)
    ├── FpsCalculator      (FPS metrics)
    └── FrameProcessor     (Frame processing)
            ├── DetectionPipeline    (Orchestration)
            │       ├── YoloFaceDetector
            │       ├── SsdFaceDetector
            │       └── HaarFaceDetector
            ├── GenderCacheManager
            └── VisionPreprocessor
```

| Metric | Before | After |
|--------|--------|-------|
| ViewController | 745 lines | 250 lines (-66%) |
| Tests | 0 | 21 |
| Configuration | Hard-coded | 30+ properties |

---

## AI Models

| Model | Format | Size | Purpose |
|-------|--------|------|---------|
| YOLOv8-face | ONNX | 12 MB | Primary detection |
| SSD ResNet-10 | Caffe | 10 MB | Fallback |
| Levi-Hassner | Caffe | 44 MB | Gender classification |
| Haar Cascade | XML | 1 MB | Emergency fallback |

---

## Testing

```bash
mvn test
```

- **MatUtilsTest** (8) — Memory utilities
- **VisionPreprocessorTest** (3) — Brightness detection
- **DetectionPipelineTest** (5) — Pipeline logic
- **Integration Tests** (5) — End-to-end scenarios

---

## Bug Fixes

- Race condition in camera capture (frame cloning)
- Memory leak in image encoding (proper Mat lifecycle)
- Code duplication (centralized `MatUtils.safeRelease()`)

---

## Changelog

- **Phase 3** — Extracted 11 constants to `application.properties`
- **Phase 2** — `FaceDetector` interface, 4 new services, ViewController refactor (745→250)
- **Phase 1** — Fixed race condition, memory leak, created `MatUtils`

---

## 🇻🇳 Tiếng Việt

### Tính Năng

- Phát hiện khuôn mặt đa engine (YOLOv8, SSD, Haar Cascade)
- Cấu hình linh hoạt qua `application.properties`
- Phân loại giới tính với mô hình Levi-Hassner
- Cập nhật tự động từ GitHub
- Giao diện Dark UI chuyên nghiệp
- Camera thời gian thực 30+ FPS
- 21 bài kiểm thử tự động
- Bộ cài đặt Windows độc lập

### Yêu Cầu

| Công cụ | Phiên bản |
|---------|-----------|
| JDK | 25 |
| Maven | 3.9+ |
| Inno Setup 6 | Để build `.exe` |

### Chạy Ứng Dụng

```bash
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition
mvn javafx:run
```

### Cấu Hình

Chỉnh sửa `src/main/resources/application.properties`:

```properties
yolo.confidence.threshold=0.65
ssd.confidence.threshold=0.60
camera.frame.interval.ms=33
vision.brightness.threshold=190.0
```

---

## License

MIT License — [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)