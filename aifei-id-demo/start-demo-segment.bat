@echo off
setlocal
cd /d "%~dp0"

rem aifei-id demo (segment + local store): HTTP on 18140, loopback only (see app-config-segment.txt)
rem Config: app-config-segment.txt -> id.strategy=segment, id.segment.step=200, id.segment.store=local
rem Endpoints: /id/demo/page  /id/demo/state  /id/demo/next
rem            /id/demo/batch?size=2000  /id/demo/segment?size=450
rem NOTICE: same port as start-demo.bat -- the two scripts must not run at the same time.
rem         Stop the running one first: stop-demo.bat
echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo (segment, local store), HTTP 18140, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18140/
echo.
java -Ddemo.config=app-config-segment.txt -Dundertow.port=18140 -Dundertow.host=127.0.0.1 -cp ".\target\classes;%CP%" cn.hg.aifei.id.demo.DemoApp
