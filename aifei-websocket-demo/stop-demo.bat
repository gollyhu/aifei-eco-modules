@echo off
setlocal

rem Stop the demo started by start-demo.bat (HTTP port 18090)
set "PORT=18090"

set "PID="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do set "PID=%%p"

if not defined PID (
    echo Demo is not running on port %PORT%
    exit /b 0
)

echo Stopping demo on port %PORT% ^(PID %PID%^)...
taskkill /f /pid %PID%
