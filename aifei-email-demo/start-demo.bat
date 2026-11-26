@echo off
setlocal
cd /d "%~dp0"

rem aifei-email demo: HTTP on 18190
rem Mock send: zero outbound network, no real email.
rem NOTICE: aifei-email must be installed to local repo first:
rem   cd ..\aifei-email ^&^& mvn -q -DskipTests clean install

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed. Ensure aifei-email:1.0.0-SNAPSHOT is installed: cd ..\aifei-email ^&^& mvn -q -DskipTests clean install
    exit /b 1
)

set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18190, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18190/email/demo/page
echo.
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dundertow.port=18190 -Dundertow.host=0.0.0.0 -cp ".\target\classes;.\src\main\resources;%CP%" cn.hg.aifei.email.demo.DemoApp
