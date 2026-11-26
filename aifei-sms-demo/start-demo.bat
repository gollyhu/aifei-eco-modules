@echo off
setlocal
cd /d "%~dp0"

rem aifei-sms demo: HTTP on 18180, loopback/host (see app-config.txt)
rem Mock send: zero outbound network, no real SMS. Receipt goes through real SmsHandler.
rem NOTICE: aifei-sms must be installed to local repo first:
rem   cd ..\aifei-sms ^&^& mvn -q -DskipTests clean install

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed. Ensure aifei-sms:1.0.0-SNAPSHOT is installed: cd ..\aifei-sms ^&^& mvn -q -DskipTests clean install
    exit /b 1
)

set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Starting demo, HTTP 18180, press Ctrl+C to stop...
echo [3/3] Verify: http://localhost:18180/sms/demo/page
echo.
java -Dundertow.port=18180 -Dundertow.host=0.0.0.0 -cp ".\target\classes;.\target\..\src\main\resources;%CP%" cn.hg.aifei.sms.demo.DemoApp
