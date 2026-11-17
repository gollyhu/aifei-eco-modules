@echo off
setlocal
cd /d "%~dp0"

rem aifei-excel demo: HTTP on 18150, loopback/host (see app-config.txt)
rem Library-module SDK demo: no Plugin, no config keys.
rem Endpoints: /excel/demo/page  /state  /parse(POST multipart)  /export-once  /export-stream?rows=50000
rem NOTICE: aifei-excel must be installed to local repo first:
rem   cd ..\aifei-excel ^&^& mvn -q -DskipTests clean install

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed. Ensure aifei-excel:1.0.0 is installed: cd ..\aifei-excel ^&^& mvn -q -DskipTests clean install
    exit /b 1
)

set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18150, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18150/excel/demo/page
echo.
java -Dundertow.port=18150 -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.excel.demo.DemoApp
