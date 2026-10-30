@echo off
setlocal
cd /d "%~dp0"

rem aifei-oss demo: HTTP on 18092, local storage backend (no cloud SDK required)
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment and run "mvn install" in aifei-oss first
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18092, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18092/oss/demo/state
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18092 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.oss.demo.DemoApp
