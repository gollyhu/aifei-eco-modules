@echo off
setlocal
cd /d "%~dp0"

rem Dashboard mode: rules are pushed by the Sentinel dashboard, SDK writes them back to sentinel-rules.json
echo [0/3] Ensure the Sentinel dashboard on port 8858...
netstat -ano | findstr ":8858" | findstr "LISTENING" >nul 2>nul
if not errorlevel 1 (
    echo       Dashboard already running: http://localhost:8858
    goto dashboard_ready
)
echo       Dashboard not running, starting it in a new window...
start "sentinel-dashboard" cmd /k call "%~dp0start-dashboard.bat"
for /l %%i in (1,1,30) do (
    timeout /t 1 /nobreak >nul
    netstat -ano | findstr ":8858" | findstr "LISTENING" >nul 2>nul
    if not errorlevel 1 goto dashboard_ready
)
echo       Dashboard not ready after 30s, continue anyway - check the dashboard window

:dashboard_ready
echo.

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem First run: copy rule template to working dir, so write-back has a target file
if not exist "sentinel-rules.json" (
    copy /y "src\main\resources\sentinel-rules.json" "sentinel-rules.json" >nul
    echo Copied rule template to .\sentinel-rules.json
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Mode: dashboard (config: app-config-dashboard.txt), start on port 18080, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18080/demo   dashboard: http://localhost:8858
echo.
java -Ddemo.config=app-config-dashboard.txt -Dundertow.port=18080 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.sentinel.demo.DemoApp
