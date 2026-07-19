[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$AppImage,

    [Parameter(Mandatory = $true)]
    [string]$DataDirectory,

    [Parameter(Mandatory = $true)]
    [string]$Output
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$appImagePath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $AppImage))
$dataPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $DataDirectory))
$outputPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Output))
$payloadRoot = Join-Path $repoRoot 'target\iexpress-payload'
$payloadApp = Join-Path $payloadRoot 'FaceRecognition'
$payloadZip = Join-Path $payloadRoot 'FaceRecognitionPayload.zip'
$sedPath = Join-Path $repoRoot 'target\FaceRecognition.iexpress.sed'
$iexpressPath = Join-Path $env:WINDIR 'System32\iexpress.exe'

foreach ($path in @($appImagePath, $dataPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw "Required packaging directory not found: $path"
    }
}
if (-not (Test-Path -LiteralPath $iexpressPath -PathType Leaf)) {
    throw "Windows IExpress was not found: $iexpressPath"
}

if (Test-Path -LiteralPath $payloadRoot) {
    Remove-Item -LiteralPath $payloadRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $payloadApp -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $outputPath) -Force | Out-Null

Copy-Item -Path (Join-Path $appImagePath '*') -Destination $payloadApp -Recurse -Force
$appData = Join-Path $payloadApp 'app\data'
New-Item -ItemType Directory -Path $appData -Force | Out-Null
Copy-Item -Path (Join-Path $dataPath '*') -Destination $appData -Recurse -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'iexpress-install.cmd') -Destination $payloadRoot -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'iexpress-install.ps1') -Destination $payloadRoot -Force

Compress-Archive -Path $payloadApp -DestinationPath $payloadZip -CompressionLevel Optimal -Force

$payloadRootWithSlash = $payloadRoot.TrimEnd('\') + '\'
$sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3

[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=1
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles

[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$outputPath
FriendlyName=Face Recognition
AppLaunched=iexpress-install.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=iexpress-install.cmd
UserQuietInstCmd=iexpress-install.cmd
FILE0="iexpress-install.cmd"
FILE1="FaceRecognitionPayload.zip"

[SourceFiles]
SourceFiles0=$payloadRootWithSlash

[SourceFiles0]
%FILE0%=
%FILE1%=
"@

[System.IO.File]::WriteAllText($sedPath, $sed.TrimStart(), [System.Text.Encoding]::ASCII)
$build = Start-Process -FilePath $iexpressPath -ArgumentList @('/N', $sedPath) -Wait -PassThru
if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
    throw "IExpress failed to create the installer. Exit code: $($build.ExitCode)"
}

Write-Host ("IExpress installer created: {0} ({1:N0} bytes)" -f $outputPath, (Get-Item -LiteralPath $outputPath).Length)
