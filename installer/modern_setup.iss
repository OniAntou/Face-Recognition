; -- modern_setup.iss --
; Professional Inno Setup Script for Face Recognition

[Setup]
AppName=Face Recognition
AppVersion=1.0
AppPublisher=Face Recognition
DefaultDirName={autopf}\FaceRecognition
DefaultGroupName=Face Recognition
AllowNoIcons=yes
LicenseFile=..\LICENSE.txt
; Modern look and feel
WizardStyle=modern
; Maximum compression for smaller installer
Compression=lzma2/ultra64
InternalCompressLevel=ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
LZMANumBlockThreads=4
; Professional Branding (Images should be converted to .bmp)
; WizardImageFile=installer_sidebar.bmp
; WizardSmallImageFile=installer_top.bmp
; SetupIconFile=..\src\main\resources\icon.ico
OutputDir=..\releases
OutputBaseFilename=FaceRecognition_Setup
; Uninstaller configuration
UninstallDisplayName=Face Recognition
UninstallDisplayIcon={app}\FaceRecognition.exe
; Better UX
DisableWelcomePage=no
DisableProgramGroupPage=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; All files from the jpackage app-image (includes EXE and JRE)
Source: "..\target\dist\FaceRecognition\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; AI Models only (exclude unused haarcascade file to save ~1MB)
Source: "..\data\deploy.prototxt"; DestDir: "{app}\app\data"; Flags: ignoreversion
Source: "..\data\res10_300x300_ssd_iter_140000.caffemodel"; DestDir: "{app}\app\data"; Flags: ignoreversion
Source: "..\data\gender_deploy.prototxt"; DestDir: "{app}\app\data"; Flags: ignoreversion
Source: "..\data\gender_net.caffemodel"; DestDir: "{app}\app\data"; Flags: ignoreversion

[Icons]
Name: "{group}\Face Recognition"; Filename: "{app}\FaceRecognition.exe"
Name: "{commondesktop}\Face Recognition"; Filename: "{app}\FaceRecognition.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\FaceRecognition.exe"; Description: "{cm:LaunchProgram,Face Recognition}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app\data"
