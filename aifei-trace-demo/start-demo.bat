@echo off
setlocal
cd /d "%~dp0"

rem aifei-trace demo: HTTP on 18130, loopback only (see app-config.txt)
rem Endpoints: /trace/demo/page  /trace/demo/state  /trace/demo/current
rem            /trace/demo/span  /trace/demo/log    /trace/demo/log/clear
rem            /trace/demo/async /trace/demo/extract
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18130, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18130/
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18130 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.trace.demo.DemoApp
