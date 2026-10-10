@echo off
setlocal
cd /d "%~dp0"

where powershell >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo PowerShell is required to run the standalone preview.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0preview.ps1"
if %ERRORLEVEL% neq 0 (
    echo Preview runner exited with error code %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)
