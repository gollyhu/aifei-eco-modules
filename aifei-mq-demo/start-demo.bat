@echo off
setlocal
cd /d "%~dp0"

rem Memory MQ demo: HTTP on 18100, mq.type=memory (in-process), no extra ports
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18100, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18100/mq/demo/state
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18100 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.mq.demo.DemoApp
