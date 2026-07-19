@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0iexpress-install.ps1"
exit /b %ERRORLEVEL%
