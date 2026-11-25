@echo off
setlocal
cd /d "%~dp0"

rem aifei-audit demo: HTTP on 18160, loopback only (-Dundertow.host=127.0.0.1; module config in app-config.txt)
rem Page: http://localhost:18160/
rem Endpoints: /audit/demo/state /events /annotated /unannotated /manual /masked /masked/off
rem            /failure /failure/evidence /sink/switch /sink/fail /login /logout /reset
echo [1/3] Build demo and classpath...
call mvn -q -o -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18160, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18160/
echo.
java -Ddemo.config=app-config.txt -Dundertow.port=18160 -Dundertow.host=127.0.0.1 -cp ".\target\classes;%CP%" cn.hg.aifei.audit.demo.DemoApp
