# Face Recognition & Gender Classification

A professional **Face Recognition** and **Gender Classification** desktop application built with **Java 17**, **OpenCV DNN**, and **JavaFX**.

Detects faces in real-time using the **SSD ResNet-10** deep learning model and predicts gender with the **Levi-Hassner CaffeNet** model — all wrapped in a modern Dark Mode UI.

---

## ✨ Features

- **AI Face Detection** — SSD ResNet-10 model for high-accuracy face detection
- **Gender Classification** — Levi-Hassner model with confidence display (e.g. "Male 92%")
- **Real-time Camera** — Live webcam detection at ~30 FPS with frame-skip optimization
- **Image Upload** — Supports PNG, JPG, JPEG, GIF, BMP, WebP formats
- **Premium Dark UI** — Modern interface powered by AtlantaFX theme
- **Responsive Layout** — Image panels auto-resize to fill available window space
- **Windows Installer** — One-click setup via Inno Setup, no Java installation required for end users

## 🛠 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 17+ | Compile & run |
| **Maven** | 3.8+ | Build & dependency management |
| **Webcam** | — | Real-time detection (optional) |
| **Inno Setup 6** | — | Build installer (optional) |

## 🚀 How to Run

### Development
```bash
# Clone the repository
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition

# Run the application
mvn javafx:run
```

### Build Executable JAR
```bash
mvn clean package
java -jar target/opencv-demo-1.0-SNAPSHOT.jar
```

### Build Windows Installer
```bash
# Requires JDK 24 + Inno Setup 6
build_installer.bat
```
This creates `releases/FaceRecognition_Setup.exe` — a standalone installer that bundles the app, JRE, and AI models. End users don't need Java installed.

## 📁 Project Structure

```
Face-Recognition/
├── data/                          # AI models
│   ├── deploy.prototxt            # Face detection config
│   ├── res10_300x300_ssd_iter_140000.caffemodel  # Face detection model
│   ├── gender_deploy.prototxt     # Gender classification config
│   └── gender_net.caffemodel      # Gender classification model
├── src/main/java/com/example/facedetection/
│   ├── MainApp.java               # Application entry point & lifecycle
│   ├── Launcher.java              # Fat JAR launcher (bypasses module check)
│   ├── controller/
│   │   └── ViewController.java    # UI controller (camera, image, display)
│   ├── service/
│   │   └── FaceDetectorService.java  # Core AI engine (detection + gender)
│   └── cli/
│       └── FaceRecognitionCli.java   # Command-line interface
├── src/main/resources/
│   ├── com/example/facedetection/
│   │   ├── scene.fxml             # UI layout
│   │   └── styles.css             # Dark theme styles
│   └── logback.xml                # Logging configuration
├── installer/
│   └── modern_setup.iss           # Inno Setup installer script
├── build_installer.bat            # One-click build + installer script
└── pom.xml                        # Maven configuration
```

## ⚡ Performance Optimizations

- **Pre-allocated constants** — Scalar/Size objects reused across frames (zero GC pressure)
- **Frame dropping** — AtomicBoolean guard skips frames when AI processing is slower than camera capture
- **Reusable encoding buffer** — Single MatOfByte for Mat→Image conversion
- **Camera tuning** — 640×480 resolution, buffer size = 1 for lowest latency
- **Daemon threads** — Camera executor won't block JVM exit
- **Thread-safe DNN** — Synchronized locks on face and gender network inference
- **Deterministic cleanup** — try/finally + safeRelease() for all native OpenCV Mat objects
- **Minimal fat JAR** — Non-Windows native libraries stripped (~75% size reduction)
- **Optimized JRE** — jlink strips debug info, headers, man pages + zip-6 compression
- **G1GC tuning** — 10ms max pause target for smooth real-time UI

## 🧠 AI Models

| Model | File | Size | Purpose |
|-------|------|------|---------|
| SSD ResNet-10 | `res10_300x300_ssd_iter_140000.caffemodel` | 10 MB | Face detection |
| Levi-Hassner CaffeNet | `gender_net.caffemodel` | 44 MB | Gender classification |

### Gender Preprocessing Pipeline
1. **Proportional padding** (40% of face dimensions) for forehead/chin context
2. **Square crop** centered on face to prevent aspect ratio distortion
3. **Resize** to 227×227 with BGR mean subtraction
4. **Confidence threshold** at 55% — below shows "Unknown"

## 📜 Credits

- **Original Authors**: [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)
- **AI Models**: [Levi & Hassner (2015)](https://talhassner.github.io/home/publication/2015_CVPR) — Age and Gender Classification using CNNs
- **Face Detection**: [OpenCV DNN SSD ResNet-10](https://github.com/opencv/opencv/tree/master/samples/dnn)

## 📄 License

See [LICENSE.txt](LICENSE.txt) for details.

---

# 🇻🇳 Tiếng Việt

# Nhận Diện Khuôn Mặt & Phân Loại Giới Tính

Ứng dụng desktop **Nhận diện khuôn mặt** và **Phân loại giới tính** chuyên nghiệp, được xây dựng bằng **Java 17**, **OpenCV DNN**, và **JavaFX**.

Phát hiện khuôn mặt theo thời gian thực bằng mô hình deep learning **SSD ResNet-10** và dự đoán giới tính bằng mô hình **Levi-Hassner CaffeNet** — tất cả trong giao diện Dark Mode hiện đại.

---

## ✨ Tính Năng

- **Phát hiện khuôn mặt bằng AI** — Mô hình SSD ResNet-10 cho độ chính xác cao
- **Phân loại giới tính** — Mô hình Levi-Hassner kèm hiển thị độ tin cậy (VD: "Male 92%")
- **Camera thời gian thực** — Phát hiện trực tiếp từ webcam ở ~30 FPS với tối ưu bỏ frame
- **Tải ảnh lên** — Hỗ trợ định dạng PNG, JPG, JPEG, GIF, BMP, WebP
- **Giao diện Dark Mode** — Giao diện hiện đại sử dụng AtlantaFX theme
- **Bố cục tự động** — Khung ảnh tự co giãn theo kích thước cửa sổ
- **Trình cài đặt Windows** — Cài đặt một click qua Inno Setup, người dùng không cần cài Java

## 🛠 Yêu Cầu

| Công cụ | Phiên bản | Mục đích |
|---------|-----------|----------|
| **JDK** | 17+ | Biên dịch & chạy |
| **Maven** | 3.8+ | Quản lý build & thư viện |
| **Webcam** | — | Phát hiện thời gian thực (tùy chọn) |
| **Inno Setup 6** | — | Tạo trình cài đặt (tùy chọn) |

## 🚀 Cách Chạy

### Phát triển
```bash
# Clone repository
git clone https://github.com/OniAntou/Face-Recognition.git
cd Face-Recognition

# Chạy ứng dụng
mvn javafx:run
```

### Tạo file JAR
```bash
mvn clean package
java -jar target/opencv-demo-1.0-SNAPSHOT.jar
```

### Tạo trình cài đặt Windows
```bash
# Yêu cầu JDK 24 + Inno Setup 6
build_installer.bat
```
Lệnh này tạo file `releases/FaceRecognition_Setup.exe` — trình cài đặt độc lập bao gồm ứng dụng, JRE, và các mô hình AI. Người dùng cuối không cần cài Java.

## 📁 Cấu Trúc Dự Án

```
Face-Recognition/
├── data/                          # Mô hình AI
│   ├── deploy.prototxt            # Cấu hình phát hiện khuôn mặt
│   ├── res10_300x300_ssd_iter_140000.caffemodel  # Mô hình phát hiện khuôn mặt
│   ├── gender_deploy.prototxt     # Cấu hình phân loại giới tính
│   └── gender_net.caffemodel      # Mô hình phân loại giới tính
├── src/main/java/com/example/facedetection/
│   ├── MainApp.java               # Điểm khởi chạy & quản lý vòng đời
│   ├── Launcher.java              # Launcher cho fat JAR
│   ├── controller/
│   │   └── ViewController.java    # Controller giao diện (camera, ảnh, hiển thị)
│   ├── service/
│   │   └── FaceDetectorService.java  # Engine AI chính (phát hiện + giới tính)
│   └── cli/
│       └── FaceRecognitionCli.java   # Giao diện dòng lệnh
├── src/main/resources/
│   ├── com/example/facedetection/
│   │   ├── scene.fxml             # Bố cục giao diện
│   │   └── styles.css             # Giao diện Dark theme
│   └── logback.xml                # Cấu hình logging
├── installer/
│   └── modern_setup.iss           # Script trình cài đặt Inno Setup
├── build_installer.bat            # Script build + tạo installer một click
└── pom.xml                        # Cấu hình Maven
```

## ⚡ Tối Ưu Hiệu Suất

- **Hằng số được cấp phát sẵn** — Đối tượng Scalar/Size tái sử dụng qua các frame (không tạo rác GC)
- **Bỏ frame thông minh** — Cờ AtomicBoolean bỏ qua frame khi xử lý AI chậm hơn camera
- **Bộ đệm encoding tái sử dụng** — Một MatOfByte duy nhất cho chuyển đổi Mat→Image
- **Tinh chỉnh camera** — Độ phân giải 640×480, buffer size = 1 cho độ trễ thấp nhất
- **Daemon thread** — Camera executor không chặn JVM thoát
- **DNN an toàn đa luồng** — Khóa synchronized trên mỗi mạng neural
- **Giải phóng bộ nhớ chắc chắn** — try/finally + safeRelease() cho mọi đối tượng OpenCV Mat
- **Fat JAR tối giản** — Loại bỏ thư viện native không dùng (~75% giảm kích thước)
- **JRE tối ưu** — jlink loại bỏ debug info, headers, man pages + nén zip-6
- **G1GC tinh chỉnh** — Mục tiêu pause tối đa 10ms cho giao diện thời gian thực mượt mà

## 🧠 Mô Hình AI

| Mô hình | File | Kích thước | Mục đích |
|---------|------|------------|----------|
| SSD ResNet-10 | `res10_300x300_ssd_iter_140000.caffemodel` | 10 MB | Phát hiện khuôn mặt |
| Levi-Hassner CaffeNet | `gender_net.caffemodel` | 44 MB | Phân loại giới tính |

### Pipeline Tiền Xử Lý Giới Tính
1. **Padding tỷ lệ** (40% kích thước mặt) để bao gồm trán/cằm
2. **Crop hình vuông** centered vào mặt để tránh méo tỷ lệ
3. **Resize** về 227×227 với trừ mean BGR
4. **Ngưỡng tin cậy** 55% — dưới ngưỡng hiển thị "Unknown"

## 📜 Tác Giả

- **Tác giả gốc**: [OniAntou](https://github.com/OniAntou), [Casluminous](https://github.com/Casluminous)
- **Mô hình AI**: [Levi & Hassner (2015)](https://talhassner.github.io/home/publication/2015_CVPR) — Phân loại tuổi và giới tính bằng CNN
- **Phát hiện khuôn mặt**: [OpenCV DNN SSD ResNet-10](https://github.com/opencv/opencv/tree/master/samples/dnn)

## 📄 Giấy Phép

Xem [LICENSE.txt](LICENSE.txt) để biết chi tiết.