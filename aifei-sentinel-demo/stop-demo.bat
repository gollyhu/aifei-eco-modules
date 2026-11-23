@echo off
setlocal

rem Stop the demo started by start-demo.bat / start-demo-dashboard.bat (default port 80)
set "PORT=80"
if defined DEMO_PORT set "PORT=%DEMO_PORT%"

rem 精确匹配本地监听端口：结尾空格避免误匹配 8080 / 18080
set "PORT_PATTERN=:%PORT% "
set "PID="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:"%PORT_PATTERN%" ^| findstr /c:"LISTENING"') do set "PID=%%p"

if not defined PID (
    echo Demo is not running on port %PORT%
    exit /b 0
)

echo Stopping demo on port %PORT% ^(PID %PID%^)...
taskkill /f /pid %PID%
