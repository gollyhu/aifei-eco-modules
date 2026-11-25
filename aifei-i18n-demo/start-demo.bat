@echo off
setlocal
cd /d "%~dp0"

rem aifei-i18n demo: HTTP on 18170, host 0.0.0.0
rem Endpoints: /i18n/demo/page  /bundle  /rtl  /money  /header  /editable  /save
rem NOTICE: aifei-i18n must be installed to local repo first:
rem   cd ..\aifei-i18n ^&^& mvn -q -DskipTests clean install

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed. Ensure aifei-i18n:1.0.0-SNAPSHOT is installed: cd ..\aifei-i18n ^&^& mvn -q -DskipTests clean install
    exit /b 1
)

set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18170, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18170/
echo.
java -Dundertow.port=18170 -Dundertow.host=0.0.0.0 -Ddemo.config=app-config.txt -cp ".\target\classes;%CP%" cn.hg.aifei.i18n.demo.DemoApp
