@echo off
setlocal
cd /d "%~dp0"

rem Local mode: rules come from sentinel-rules.json, edit it to take effect within seconds
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem First run: copy rule template to working dir, so local hot-reload works
if not exist "sentinel-rules.json" (
    copy /y "src\main\resources\sentinel-rules.json" "sentinel-rules.json" >nul
    echo Copied rule template to .\sentinel-rules.json
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Mode: local (config: app-config.txt), start on port 18080, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18080/demo
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18080 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.demo.DemoApp
