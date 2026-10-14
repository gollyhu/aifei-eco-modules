@echo off
setlocal
cd /d "%~dp0"

rem Loopback demo: HTTP on 18090, WebSocket server on 18091, client connects to ws://127.0.0.1:18091/ws
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18090, WebSocket server 18091, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18090/ws/demo/state
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18090 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.websocket.demo.DemoApp
