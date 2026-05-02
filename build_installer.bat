@echo off
setlocal enabledelayedexpansion
echo ==========================================
echo   FACE RECOGNITION - INSTALLER BUILDER
echo ==========================================
echo.

REM Ensure we're in the correct directory
cd /d "%~dp0"

echo Step 0: Ensuring application is closed and cleaning up...
taskkill /F /IM FaceRecognition.exe /T >nul 2>&1
if exist "target" rd /s /q "target"
if exist "releases\FaceRecognition_Setup.exe" del "releases\FaceRecognition_Setup.exe"

echo Step 1: Building project with Maven...
echo Current directory: %CD%

REM Check if pom.xml exists
if not exist "pom.xml" (
    echo.
    echo ERROR: pom.xml not found in current directory!
    echo Please run this script from the Face-Recognition project root.
    pause
    exit /b 1
)

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

REM Verify data folder exists
if not exist "data" (
    echo ERROR: 'data' folder not found! AI models are required.
    pause
    exit /b 1
)

echo Step 2: Preparing clean input directory...
REM Ensure dependency folder exists (created by maven-dependency-plugin in pom.xml)
if not exist "target\dependency" (
    echo ERROR: target\dependency not found! Ensure maven-dependency-plugin is configured.
    pause
    exit /b 1
)

if exist "target\libs" rd /s /q "target\libs"
mkdir "target\libs"

REM Copy dependencies and the main jar to a single flat folder for jpackage
copy "target\dependency\*.jar" "target\libs\" /Y
set "MAIN_JAR="
for %%f in (target\opencv-demo-*.jar) do (
    set "FILE_NAME=%%~nxf"
    REM Skip sources, javadoc and test jars
    echo !FILE_NAME! | findstr /i "sources javadoc tests" >nul
    if errorlevel 1 (
        set "MAIN_JAR=!FILE_NAME!"
        copy "%%f" "target\libs\" /Y
    )
)

if "!MAIN_JAR!"=="" (
    echo ERROR: Main JAR not found in target!
    pause
    exit /b 1
)

echo Step 3: Creating App Image with jpackage...
"%JAVA_HOME%\bin\jpackage.exe" ^
    --type app-image ^
    --name FaceRecognition ^
    --input target\libs ^
    --main-jar !MAIN_JAR! ^
    --main-class com.example.facedetection.Launcher ^
    --dest target/dist ^
    --vendor "Face Recognition" ^
    --java-options "-XX:+UseSerialGC -Xms32m -Xmx320m -XX:+UseStringDeduplication" ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands --compress zip-9"
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

echo Step 4: Compiling Installer with Inno Setup...
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
