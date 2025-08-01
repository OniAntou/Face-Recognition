# FaceRecognition

Face Recognition using Java Language + OpenCV Library

## Prerequisites

Ensure the following dependencies are installed and configured:

- **Java 24**: Verify with `java --version`. Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or use a package manager like `sdkman` or `choco`.
- **Maven**: Verify with `mvn --version`. Install from [Maven's official site](https://maven.apache.org/download.cgi) or a package manager.
- **JavaFX**: Ensure the JavaFX SDK is installed and configured in your `pom.xml` or environment variables. Download from [Gluon](https://gluonhq.com/products/javafx/).
- **OpenCV**: Install OpenCV and configure native libraries for your platform. Ensure OpenCV dependencies are correctly specified in your `pom.xml`.

## Running the Application

Follow these steps to run the FaceRecognition application:

1. **Open Terminal or PowerShell**:
   - **Windows**: Open PowerShell by searching for "PowerShell" in the Start menu.
   - **macOS/Linux**: Open the Terminal application.

2. **Navigate to the Project Directory**:
   Use the `cd` command to change to the `FaceRecognition` folder. Replace `<path-to-folder>` with the actual path to your project directory.

   ```bash
   cd <path-to-folder>/FaceRecognition
   ```

   Example:
   ```bash
   cd ~/Projects/FaceRecognition
   ```

   **Note**:
   - Use forward slashes (`/`) for macOS/Linux or backslashes (`\`) for Windows.
   - Verify the current directory with `pwd` (macOS/Linux) or `Get-Location` (PowerShell).

3. **Execute the Maven Command**:
   Run the following command to build and execute the JavaFX application:

   ```bash
   mvn javafx:run
   ```

## Example Terminal/PowerShell Session

```bash
# Navigate to the FaceRecognition folder
cd ~/Projects/FaceRecognition

# Run the JavaFX application
mvn javafx:run
```

## Troubleshooting

- **Maven not recognized**: Ensure Maven is added to your system’s `PATH`.
- **JavaFX/OpenCV errors**: Verify that dependencies are correctly specified in your `pom.xml` and that native libraries are accessible.
- **Java version issues**: Confirm compatibility between Java 24, JavaFX, and OpenCV versions.

For further assistance, refer to the official documentation for [JavaFX](https://openjfx.io/) and [OpenCV](https://opencv.org/).

## Credit

- [OniAntou](https://github.com/OniAntou)
- [Casluminous](https://github.com/Casluminous)