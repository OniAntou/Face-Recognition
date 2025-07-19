@echo off
title Face Recognition
color 0A

:: Change to script directory
pushd "%~dp0"

:: Check admin rights
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Please run as administrator
    echo Right-click and select "Run as administrator"
    pause
    exit /b 1
)

echo Face Recognition Setup
echo ====================
echo.

:: Check and install Java if needed
echo Checking Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Java not found. Installing Java 24...
    echo This may take a few minutes...
    
    :: Create temp directory
    set "TEMP_DIR=%TEMP%\FaceRecognitionSetup"
    mkdir "%TEMP_DIR%" 2>nul
    
    :: Download and install Java
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download.oracle.com/java/24/latest/jdk-24_windows-x64_bin.exe' -OutFile '%TEMP_DIR%\java_installer.exe'}"
    start /wait %TEMP_DIR%\java_installer.exe /s
    
    :: Clean up
    rd /s /q "%TEMP_DIR%" 2>nul
)

:: Check and install JavaFX if needed
echo Checking JavaFX...
if not exist "C:\Program Files\Java\javafx-sdk-24.0.2\lib\javafx.graphics.jar" (
    echo JavaFX not found. Installing JavaFX 24...
    echo This may take a few minutes...
    
    :: Create temp directory
    set "TEMP_DIR=%TEMP%\FaceRecognitionSetup"
    mkdir "%TEMP_DIR%" 2>nul
    
    :: Download and install JavaFX
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download2.gluonhq.com/openjfx/24.0.2/openjfx-24.0.2_windows-x64_bin-sdk.zip' -OutFile '%TEMP_DIR%\javafx.zip'}"
    powershell -Command "& {Expand-Archive -Path '%TEMP_DIR%\javafx.zip' -DestinationPath 'C:\Program Files\Java' -Force}"
    
    :: Clean up
    rd /s /q "%TEMP_DIR%" 2>nul
)

:: Extract OpenCV if needed
echo Setting up OpenCV...
if not exist "opencv_dlls" mkdir opencv_dlls >nul 2>&1
if not exist "opencv_dlls\opencv_java490.dll" (
    jar xf "Face Recognition.jar" nu/pattern/opencv/windows/x86_64/ >nul 2>&1
    move "nu\pattern\opencv\windows\x86_64\*.dll" "opencv_dlls\" >nul 2>&1
    rd /s /q "nu" >nul 2>&1
)

:: Set environment
set "PATH=%CD%\opencv_dlls;%PATH%"
set "JAVA_OPTS=-Dglass.win.uiScale=100%% -Dprism.order=sw,d3d -Dprism.maxvram=512m -Djavafx.platform=win"

:: Start application
echo.
echo Starting Face Recognition...
echo The application will continue running after this window closes.
echo.

:: Launch application in background
start /b "" javaw %JAVA_OPTS% ^
    --enable-native-access=javafx.graphics,ALL-UNNAMED ^
    --add-opens java.base/java.lang=ALL-UNNAMED ^
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
    --add-opens java.base/java.io=ALL-UNNAMED ^
    -Dfile.encoding=UTF-8 ^
    --module-path "C:\Program Files\Java\javafx-sdk-24.0.2\lib" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics ^
    -cp "Face Recognition.jar" com.example.facedetection.MainApp

:: Wait a moment to check if the application started
timeout /t 2 >nul

:: Check if application is running
tasklist /FI "IMAGENAME eq javaw.exe" 2>NUL | find /I /N "javaw.exe" >NUL
if %errorlevel% neq 0 (
    echo.
    echo Failed to start Face Recognition.
    echo Please try running the script again as administrator.
    echo.
    pause
) else (
    :: Success - close this window after 3 seconds
    echo Application started successfully!
    echo This window will close in 3 seconds...
    timeout /t 3 >nul
)

:: Restore original directory
popd 