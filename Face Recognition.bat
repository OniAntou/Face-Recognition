@echo off
title Face Recognition
color 0A

:menu
cls
echo Face Recognition Setup Menu
echo =========================
echo 1. Install/Update Dependencies
echo 2. Build Application
echo 3. Run Application
echo 4. Exit
echo.
set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" goto install_deps
if "%choice%"=="2" goto build_app
if "%choice%"=="3" goto run_app
if "%choice%"=="4" goto end
goto menu

:install_deps
cls
echo Installing Dependencies
echo =====================
echo.

:: Change to script directory
pushd "%~dp0"

:: Check admin rights
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Please run as administrator
    echo Right-click and select "Run as administrator"
    pause
    goto menu
)

:: Check and install Java if needed
echo Checking Java...
java -version >nul 2>&1
set JAVA_CHECK=%errorlevel%
if %JAVA_CHECK% neq 0 (
    echo Java not found. Installing Java 24...
    echo This may take a few minutes...
    echo.
    echo Press any key to start Java installation...
    pause >nul
    
    :: Create temp directory
    echo Creating temporary directory...
    set "TEMP_DIR=%TEMP%\FaceRecognitionSetup"
    mkdir "%TEMP_DIR%" 2>nul
    
    :: Download Java
    echo Downloading Java 24...
    echo This may take several minutes depending on your internet connection...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download.oracle.com/java/24/latest/jdk-24_windows-x64_bin.exe' -OutFile '%TEMP_DIR%\java_installer.exe'}"
    if %errorlevel% neq 0 (
        echo Failed to download Java installer.
        echo Please check your internet connection and try again.
        pause
        goto menu
    )
    
    :: Install Java
    echo.
    echo Installing Java...
    echo Please wait while Java is being installed...
    echo This window will appear to freeze - this is normal.
    echo.
    start /wait %TEMP_DIR%\java_installer.exe /s
    
    :: Clean up
    echo Cleaning up temporary files...
    rd /s /q "%TEMP_DIR%" 2>nul
    
    :: Verify Java installation
    echo.
    echo Verifying Java installation...
    java -version >nul 2>&1
    if %errorlevel% neq 0 (
        echo.
        echo Java installation may have failed.
        echo Please try installing Java manually from https://www.oracle.com/java/technologies/downloads/
        echo.
        pause
        goto menu
    )
    
    echo.
    echo Java has been installed successfully!
    echo.
    pause
) else (
    echo Java is already installed.
    java -version
    echo.
    pause
)

:: Check and install Maven if needed
echo.
echo Checking Maven...
echo Current PATH: %PATH%
echo.
pause

:: Try to find Maven in common locations
set "MAVEN_FOUND=0"
if exist "C:\Program Files\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MAVEN_FOUND=1"
    set "MAVEN_HOME=C:\Program Files\apache-maven-3.9.6"
    set "PATH=%PATH%;C:\Program Files\apache-maven-3.9.6\bin"
)

if exist "C:\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MAVEN_FOUND=1"
    set "MAVEN_HOME=C:\apache-maven-3.9.6"
    set "PATH=%PATH%;C:\apache-maven-3.9.6\bin"
)

where mvn 2>nul
if %errorlevel% equ 0 (
    set "MAVEN_FOUND=1"
)

if %MAVEN_FOUND% equ 0 (
    echo Maven not found. Installing Maven...
    echo This may take a few minutes...
    echo.
    echo Press any key to start Maven installation...
    pause >nul
    
    :: Create temp directory
    echo Creating temporary directory...
    set "TEMP_DIR=%TEMP%\FaceRecognitionSetup"
    mkdir "%TEMP_DIR%" 2>nul
    
    :: Download Maven
    echo Downloading Maven...
    echo This may take several minutes depending on your internet connection...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP_DIR%\maven.zip'}"
    if %errorlevel% neq 0 (
        echo Failed to download Maven.
        echo Please check your internet connection and try again.
        pause
        goto menu
    )
    
    echo Installing Maven...
    powershell -Command "& {Expand-Archive -Path '%TEMP_DIR%\maven.zip' -DestinationPath 'C:\Program Files' -Force}"
    if %errorlevel% neq 0 (
        echo Failed to extract Maven.
        echo Please make sure you have enough disk space and try again.
        pause
        goto menu
    )
    
    :: Set Maven environment variables
    echo Setting up Maven environment...
    echo Current PATH before setx: %PATH%
    setx MAVEN_HOME "C:\Program Files\apache-maven-3.9.6" /M
    setx PATH "%PATH%;C:\Program Files\apache-maven-3.9.6\bin" /M
    set "PATH=%PATH%;C:\Program Files\apache-maven-3.9.6\bin"
    echo Current PATH after setx: %PATH%
    echo.
    pause
    
    :: Verify Maven installation
    echo Verifying Maven installation...
    echo Looking for mvn in:
    where mvn
    if %errorlevel% neq 0 (
        echo Maven installation failed.
        echo Please try installing Maven manually.
        pause
        goto menu
    )
    
    :: Clean up
    echo Cleaning up temporary files...
    rd /s /q "%TEMP_DIR%" 2>nul
    
    echo.
    echo Maven has been installed successfully!
    echo.
    pause
) else (
    echo Maven is already installed.
    call mvn -version
    echo.
    pause
)

:: Check and install JavaFX if needed
echo.
echo Checking JavaFX...
if not exist "C:\Program Files\Java\javafx-sdk-24.0.2\lib\javafx.graphics.jar" (
    echo JavaFX not found. Installing JavaFX 24...
    echo This may take a few minutes...
    echo.
    echo Press any key to start JavaFX installation...
    pause >nul
    
    :: Create temp directory
    echo Creating temporary directory...
    set "TEMP_DIR=%TEMP%\FaceRecognitionSetup"
    mkdir "%TEMP_DIR%" 2>nul
    
    :: Download JavaFX
    echo Downloading JavaFX...
    echo This may take several minutes depending on your internet connection...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download2.gluonhq.com/openjfx/24.0.2/openjfx-24.0.2_windows-x64_bin-sdk.zip' -OutFile '%TEMP_DIR%\javafx.zip'}"
    if %errorlevel% neq 0 (
        echo Failed to download JavaFX.
        echo Please check your internet connection and try again.
        pause
        goto menu
    )
    
    echo Installing JavaFX...
    powershell -Command "& {Expand-Archive -Path '%TEMP_DIR%\javafx.zip' -DestinationPath 'C:\Program Files\Java' -Force}"
    if %errorlevel% neq 0 (
        echo Failed to extract JavaFX.
        echo Please make sure you have enough disk space and try again.
        pause
        goto menu
    )
    
    :: Clean up
    echo Cleaning up temporary files...
    rd /s /q "%TEMP_DIR%" 2>nul
    
    echo.
    echo JavaFX has been installed successfully!
    echo.
    pause
) else (
    echo JavaFX is already installed.
    echo.
    pause
)

echo.
echo All dependencies installed successfully!
echo.
pause
goto menu

:build_app
cls
echo Building Application
echo ==================
echo.

:: Change to script directory
pushd "%~dp0"
echo Current directory after pushd:
cd
echo.
pause

:: Check if Maven is in PATH
echo Checking Maven configuration...
echo Current PATH: %PATH%
echo.
where mvn
if %errorlevel% neq 0 (
    echo Maven not found in PATH, searching for installation...
    :: Try to find Maven in common locations
    set "MAVEN_FOUND=0"
    
    if exist "C:\Program Files\apache-maven-3.9.6\bin\mvn.cmd" (
        set "MAVEN_FOUND=1"
        set "MAVEN_HOME=C:\Program Files\apache-maven-3.9.6"
        set "PATH=%PATH%;C:\Program Files\apache-maven-3.9.6\bin"
        echo Found Maven in C:\Program Files\apache-maven-3.9.6
    )

    if exist "C:\apache-maven-3.9.6\bin\mvn.cmd" (
        set "MAVEN_FOUND=1"
        set "MAVEN_HOME=C:\apache-maven-3.9.6"
        set "PATH=%PATH%;C:\apache-maven-3.9.6\bin"
        echo Found Maven in C:\apache-maven-3.9.6
    )

    if %MAVEN_FOUND% equ 0 (
        echo Maven is not installed or not found in PATH.
        echo Please run option 1 first to install dependencies.
        echo After installation, please close this window and run the batch file again.
        echo.
        pause
        goto menu
    )
    
    echo Updated PATH: %PATH%
    echo.
    echo Testing Maven again...
    where mvn
    if %errorlevel% neq 0 (
        echo Still cannot find Maven after updating PATH.
        echo Please try running option 1 again to reinstall Maven.
        pause
        goto menu
    )
)

:: Build the application
echo Building application...
echo Current directory:
cd
echo.

echo Testing Maven version...
call mvn -version
if %errorlevel% neq 0 (
    echo Failed to run Maven version check.
    echo Please ensure Maven is properly installed.
    pause
    goto menu
)

echo.
echo Running Maven build...
echo This may take several minutes...
echo.
call mvn clean package
set BUILD_RESULT=%errorlevel%
echo.
echo Maven build completed with exit code: %BUILD_RESULT%
if %BUILD_RESULT% neq 0 (
    echo Failed to build the application.
    echo Please check your internet connection and try again.
    echo If the error persists, try running option 1 first to install dependencies.
    pause
    goto menu
)

:: Copy the built JAR to the correct location
echo.
echo Copying built JAR file...
if not exist "target\opencv-demo-1.0-SNAPSHOT.jar" (
    echo Built JAR file not found in target directory.
    echo Build may have failed.
    pause
    goto menu
)

copy /Y "target\opencv-demo-1.0-SNAPSHOT.jar" "Face Recognition.jar"
if %errorlevel% neq 0 (
    echo Failed to copy JAR file.
    pause
    goto menu
)

echo.
echo Application built successfully!
echo JAR file copied to: Face Recognition.jar
echo.
pause
goto menu

:run_app
cls
echo Running Application
echo =================
echo.

:: Change to script directory
pushd "%~dp0"

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
    goto menu
) else (
    echo Application started successfully!
    echo Returning to menu in 3 seconds...
    timeout /t 3 >nul
)

goto menu

:end
echo Thank you for using Face Recognition!
pause
exit /b 0 