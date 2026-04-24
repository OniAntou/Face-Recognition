# Face Recognition & Analysis (Professional Edition)

A high-performance **Face Recognition**, **Gender Classification**, and **Real-time Analysis** desktop application built with **Java 25**, **OpenCV**, and **JavaFX**.

This version features a robust hybrid AI engine utilizing **YOLOv8-face** (via ONNX) for ultra-fast detection and **SSD ResNet-10** as a fallback, with integrated **Levi-Hassner** gender classification.

---

## ✨ Key Features

- **Hybrid AI Detection** — Support for **YOLOv8-face** (ONNX) and **SSD ResNet-10** (Caffe)
- **Gender Classification** — High-accuracy Levi-Hassner model with confidence scoring
- **Silent Updates** — In-app update system that automatically detects and installs newer versions from GitHub
- **Hardware Acceleration** — Automatic OpenCL/GPU acceleration for DNN inference
- **Premium Dark UI** — Modern, professional interface powered by the **AtlantaFX** theme
- **Real-time Camera** — Optimized 30+ FPS stream with frame-skip protection and zero-latency buffering
- **Windows Installer** — One-click professional setup bundling its own JRE and AI models

## 🛠 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 25 | Compile & run (Optimized for modern JVM) |
| **Maven** | 3.9+ | Build & dependency management |
| **Inno Setup 6** | — | Required only for building the `.exe` installer |

## 🚀 Getting Started

### Development Mode
```bash
# Clone the repository
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition

# Run using Maven
mvn javafx:run
```

### Building the Installer (Windows)
```bash
# Requires JDK 25 + Inno Setup 6
# Automatically optimizes JRE size and bundles AI models
build_installer.bat
```
The output will be in `releases/FaceRecognition_Setup.exe`. This installer is standalone and does **not** require the end user to have Java installed.

## 📁 Project Structure

```
Face-Recognition/
├── data/                          # AI Model weights & configs
│   ├── yolov8n-face.onnx          # YOLOv8 face detection (Primary)
│   ├── res10_300x300_ssd_...      # SSD ResNet-10 (Fallback)
│   └── gender_net.caffemodel      # Gender classification
├── src/main/java/com/example/facedetection/
│   ├── service/
│   │   ├── YoloFaceService.java   # YOLOv8 ONNX implementation
│   │   └── FaceDetectorService.java # SSD & Gender engine
│   └── controller/
│       └── ViewController.java    # UI logic & Silent Update engine
├── installer/                     # Inno Setup deployment scripts
└── build_installer.bat            # Automated build pipeline
```

## ⚡ Performance Optimizations

- **JVM 25 Intrinsic Support** — Leveraging modern Java features for better memory management.
- **Atomic Processing Guard** — Prevents UI lag by skipping frames if the AI thread is busy.
- **Zero-Copy Mat Buffer** — Reusable `MatOfByte` buffers for high-speed BMP encoding.
- **G1GC Tuning** — Hard-coded 10ms GC pause target for stutter-free video playback.
- **Hardware Backend** — Preference for `DNN_TARGET_OPENCL` to offload work to the GPU.

## 🧠 AI Model Specs

| Model | Format | Size | Backend |
|-------|--------|------|---------|
| **YOLOv8-face** | ONNX | 12 MB | OpenCV DNN (Primary) |
| **SSD ResNet-10**| Caffe | 10 MB | OpenCV DNN (Fallback) |
| **Levi-Hassner** | Caffe | 44 MB | Gender Classification |

## 🔄 Update Mechanism

The application checks for updates on startup by comparing the local `Build-Time` (from Manifest) with the latest release metadata on GitHub. If a newer version is found:
1. User is notified via the status bar.
2. Clicking the notification downloads the installer to a temp directory.
3. The installer is launched with `/SILENT` flags, and the app exits to allow a seamless overwrite.

## 📜 Credits & License

- **Authors**: [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)
- **License**: [MIT License](LICENSE.txt)

---

# 🇻🇳 Tiếng Việt

# Nhận Diện & Phân Tích Khuôn Mặt (Professional Edition)

Ứng dụng desktop **Nhận diện khuôn mặt**, **Phân loại giới tính** và **Phân tích thời gian thực** hiệu suất cao, được xây dựng bằng **Java 25**, **OpenCV** và **JavaFX**.

Phiên bản này sở hữu engine AI lai (hybrid) sử dụng **YOLOv8-face** (qua ONNX) để phát hiện cực nhanh và **SSD ResNet-10** làm dự phòng, tích hợp cùng hệ thống phân loại giới tính **Levi-Hassner**.

---

## ✨ Tính Năng Nổi Bật

- **Phát hiện AI Hybrid** — Hỗ trợ song song **YOLOv8-face** (ONNX) và **SSD ResNet-10** (Caffe).
- **Phân loại giới tính** — Mô hình Levi-Hassner độ chính xác cao với thang điểm tin cậy.
- **Cập nhật tự động (Silent Update)** — Tự động phát hiện và cài đặt phiên bản mới từ GitHub ngay trong app.
- **Tăng tốc phần cứng** — Tự động sử dụng OpenCL/GPU để xử lý AI.
- **Giao diện Dark UI Cao cấp** — Giao diện chuyên nghiệp, hiện đại dựa trên theme **AtlantaFX**.
- **Camera thời gian thực** — Luồng stream 30+ FPS mượt mà với cơ chế chống lag và buffer độ trễ thấp.
- **Bộ cài đặt Windows** — Cài đặt một click chuyên nghiệp, đóng gói sẵn JRE và các mô hình AI.

## 🛠 Yêu Cầu Hệ Thống

| Công cụ | Phiên bản | Mục đích |
|---------|-----------|----------|
| **JDK** | 25 | Biên dịch & chạy (Tối ưu cho JVM hiện đại) |
| **Maven** | 3.9+ | Quản lý build và thư viện |
| **Inno Setup 6** | — | Chỉ cần khi muốn đóng gói file `.exe` |

## 🚀 Hướng Dẫn Chạy

### Chế độ Phát triển
```bash
# Clone repository
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition

# Chạy bằng Maven
mvn javafx:run
```

### Đóng gói Trình cài đặt (Windows)
```bash
# Yêu cầu JDK 25 + Inno Setup 6
build_installer.bat
```
File kết quả sẽ nằm tại `releases/FaceRecognition_Setup.exe`. Đây là trình cài đặt độc lập, người dùng cuối **không cần** cài đặt Java.

## 📁 Cấu Trúc Dự Án

```
Face-Recognition/
├── data/                          # Trọng số & cấu hình mô hình AI
│   ├── yolov8n-face.onnx          # YOLOv8 face detection (Chính)
│   ├── res10_300x300_ssd_...      # SSD ResNet-10 (Dự phòng)
│   └── gender_net.caffemodel      # Phân loại giới tính
├── src/main/java/com/example/facedetection/
│   ├── service/
│   │   ├── YoloFaceService.java   # Triển khai YOLOv8 ONNX
│   │   └── FaceDetectorService.java # Engine SSD & Giới tính
│   └── controller/
│       └── ViewController.java    # Logic UI & Engine Cập nhật tự động
```

## ⚡ Tối Ưu Hiệu Suất

- **Hỗ trợ JVM 25 Intrinsic** — Tận dụng các tính năng Java mới nhất để quản lý bộ nhớ tốt hơn.
- **Cơ chế Atomic Processing** — Ngăn lag UI bằng cách bỏ qua frame nếu luồng AI đang bận.
- **Zero-Copy Mat Buffer** — Tái sử dụng bộ đệm `MatOfByte` để encode BMP tốc độ cao.
- **Tinh chỉnh G1GC** — Thiết lập mục tiêu pause GC 10ms để video không bị khựng.
- **Hardware Backend** — Ưu tiên `DNN_TARGET_OPENCL` để đẩy tải xử lý sang GPU.

## 🧠 Thông Số Mô Hình AI

| Mô hình | Định dạng | Kích thước | Backend |
|---------|-----------|------------|---------|
| **YOLOv8-face** | ONNX | 12 MB | OpenCV DNN (Chính) |
| **SSD ResNet-10**| Caffe | 10 MB | OpenCV DNN (Dự phòng) |
| **Levi-Hassner** | Caffe | 44 MB | Phân loại giới tính |

## 🔄 Cơ Chế Cập Nhật

Ứng dụng kiểm tra cập nhật khi khởi động bằng cách so sánh `Build-Time` nội bộ với metadata phiên bản mới nhất trên GitHub. Nếu có bản mới:
1. Người dùng được thông báo qua thanh trạng thái.
2. Nhấn vào thông báo sẽ tải bộ cài về thư mục tạm.
3. Bộ cài được chạy với tham số `/SILENT`, app tự đóng để quá trình ghi đè diễn ra mượt mà.

## 📜 Tác Giả & Giấy Phép

- **Tác giả**: [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)
- **Giấy phép**: [MIT License](LICENSE.txt)