$ErrorActionPreference = 'Stop'

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-IsAdministrator)) {
    $arguments = @(
        '-NoLogo'
        '-NoProfile'
        '-ExecutionPolicy'
        'Bypass'
        '-File'
        ('"{0}"' -f $PSCommandPath)
        '-Elevated'
    )
    $elevated = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $arguments -Wait -PassThru
    exit $elevated.ExitCode
}

$payloadZip = Join-Path $PSScriptRoot 'FaceRecognitionPayload.zip'
if (-not (Test-Path -LiteralPath $payloadZip -PathType Leaf)) {
    throw "Installer payload not found: $payloadZip"
}

$extractRoot = Join-Path $env:TEMP ("FaceRecognitionInstall_{0}" -f [Guid]::NewGuid().ToString('N'))
$appTarget = Join-Path ${env:ProgramFiles} 'FaceRecognition'

try {
    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    Expand-Archive -LiteralPath $payloadZip -DestinationPath $extractRoot -Force

    $appSource = Join-Path $extractRoot 'FaceRecognition'
    $launcher = Join-Path $appSource 'FaceRecognition.exe'
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        throw "Packaged application launcher not found: $launcher"
    }

    $running = @(Get-Process -Name 'FaceRecognition' -ErrorAction SilentlyContinue)
    foreach ($process in $running) {
        if ($process.MainWindowHandle -ne 0) {
            $null = $process.CloseMainWindow()
        }
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 250
        $running = @(Get-Process -Name 'FaceRecognition' -ErrorAction SilentlyContinue)
    } while ($running.Count -gt 0 -and [DateTime]::UtcNow -lt $deadline)

    if ($running.Count -gt 0) {
        $running | Stop-Process -Force
        Start-Sleep -Milliseconds 500
    }

    New-Item -ItemType Directory -Path $appTarget -Force | Out-Null
    Copy-Item -Path (Join-Path $appSource '*') -Destination $appTarget -Recurse -Force

    $startMenu = Join-Path ${env:ProgramData} 'Microsoft\Windows\Start Menu\Programs'
    New-Item -ItemType Directory -Path $startMenu -Force | Out-Null
    $shortcutPath = Join-Path $startMenu 'Face Recognition.lnk'
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $appTarget 'FaceRecognition.exe'
    $shortcut.WorkingDirectory = $appTarget
    $shortcut.Description = 'Face Recognition'
    $shortcut.Save()

    Start-Process -FilePath (Join-Path $appTarget 'FaceRecognition.exe') -WorkingDirectory $appTarget
} finally {
    if (Test-Path -LiteralPath $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
