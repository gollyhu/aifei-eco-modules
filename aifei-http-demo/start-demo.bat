@echo off
setlocal
cd /d "%~dp0"

rem aifei-http demo: HTTP on 18120, loopback only (see app-config.txt)
rem Endpoints: /http/demo/state  /http/demo/get  /http/demo/retry  /http/demo/timeout
rem            /http/demo/interceptor  /http/demo/gzip  /http/demo/redirect  /http/demo/send
rem Echo target: /demo/echo (controlled by status / delay / failFirst / gzip / redirect / text)
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18120, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18120/
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18120 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.http.demo.DemoApp
