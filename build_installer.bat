@echo off
echo ==========================================
echo   FACE RECOGNITION - INSTALLER BUILDER
echo ==========================================
echo.
echo Step 1: Building project with Maven...
set "JAVA_HOME=C:\Users\USER\.jdk\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call mvn clean package -q -DskipTests
if errorlevel 1 (
    echo.
    echo ERROR: Maven build failed! Please check the output above.
    pause
    exit /b 1
)
echo Maven build completed successfully.
echo.
echo Step 2: Creating App Image with jpackage...
if exist "target\dist" rd /s /q "target\dist"
"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --name FaceRecognition ^
    --input target ^
    --main-jar opencv-demo-1.0-SNAPSHOT.jar ^
    --main-class com.example.facedetection.Launcher ^
    --dest target/dist ^
    --vendor "Face Recognition" ^
    --java-options "-XX:+UseG1GC -XX:MaxGCPauseMillis=10 -Xms64m -Xmx512m" ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --compress zip-6"
if errorlevel 1 (
    echo.
    echo ERROR: jpackage failed! Please check the output above.
    pause
    exit /b 1
)
echo App Image created successfully.
echo.

REM Remove unused model file from app image
echo Step 2b: Cleaning up unused files...
if exist "target\dist\FaceRecognition\app\haarcascade_frontalface_default.xml" (
    del "target\dist\FaceRecognition\app\haarcascade_frontalface_default.xml"
)

echo Step 3: Compiling Installer with Inno Setup...
echo Searching for ISCC.exe...

set "ISCC_PATH=%LocalAppData%\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC_PATH%" set "ISCC_PATH=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"

if exist "%ISCC_PATH%" (
    "%ISCC_PATH%" "installer\modern_setup.iss"
    if errorlevel 1 (
        echo.
        echo ERROR: Inno Setup compilation failed!
        pause
        exit /b 1
    )
    echo.
    echo ==========================================
    echo   SUCCESS! Installer ready in 'releases'
    echo ==========================================
) else (
    echo.
    echo ERROR: Inno Setup 6 not found at %ISCC_PATH%
    echo Please install Inno Setup 6 or update the path in this script.
    echo Download: https://jrsoftware.org/isdl.php
)
pause
