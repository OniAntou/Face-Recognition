[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$hash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLowerInvariant()
$fileName = [System.IO.Path]::GetFileName($FilePath)
$line = "$hash *$fileName$([Environment]::NewLine)"
[System.IO.File]::WriteAllText($OutputPath, $line, [System.Text.Encoding]::ASCII)
Write-Host "SHA-256 written to $OutputPath"
