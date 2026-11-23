@echo off
setlocal
cd /d "%~dp0"

rem 端口：命令行首个参数优先，其次 DEMO_PORT 环境变量，默认 80
if "%~1"=="" goto use_env_port
set "DEMO_PORT=%~1"
goto port_check
:use_env_port
if not defined DEMO_PORT set "DEMO_PORT=80"

:port_check
rem 精确匹配本地监听端口：结尾空格避免误匹配 8080 / 18080
set "PORT_PATTERN=:%DEMO_PORT% "
netstat -ano | findstr /c:"%PORT_PATTERN%" | findstr /c:"LISTENING" >nul 2>nul
if errorlevel 1 goto port_free

echo [ERROR] Port %DEMO_PORT% is already in use.
echo        Listening process^(es^):
netstat -ano | findstr /c:"%PORT_PATTERN%" | findstr /c:"LISTENING"
echo.
echo        Free it with one of:
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /c:"%PORT_PATTERN%" ^| findstr /c:"LISTENING"') do echo            taskkill /f /pid %%p
echo        Or pick a free port, e.g.:  set DEMO_PORT=18080  then run this script again.
echo        This script never switches ports silently.
exit /b 1

:port_free
rem Dashboard mode: rules are pushed by the Sentinel dashboard, SDK writes them back to sentinel-rules.json
echo [0/3] Ensure the Sentinel dashboard on port 8858...
netstat -ano | findstr /c:":8858 " | findstr /c:"LISTENING" >nul 2>nul
if not errorlevel 1 (
    echo       Dashboard already running: http://localhost:8858
    goto dashboard_ready
)
echo       Dashboard not running, starting it in a new window...
start "sentinel-dashboard" cmd /k call "%~dp0start-dashboard.bat"
for /l %%i in (1,1,30) do (
    timeout /t 1 /nobreak >nul
    netstat -ano | findstr /c:":8858 " | findstr /c:"LISTENING" >nul 2>nul
    if not errorlevel 1 goto dashboard_ready
)
echo       Dashboard not ready after 30s, continue anyway - check the dashboard window

:dashboard_ready
echo.

echo [1/3] Build demo and classpath...
call mvn -q -f .\pom.xml compile dependency:build-classpath -Dmdep.outputFile=target\cp.txt
if errorlevel 1 (
    echo Build failed, please check Maven environment
    exit /b 1
)

rem First run: copy rule template to working dir, so write-back has a target file
if not exist "sentinel-rules.json" (
    copy /y "src\main\resources\sentinel-rules.json" "sentinel-rules.json" >nul
    echo Copied rule template to .\sentinel-rules.json
)

rem set /p reads at most ~1024 chars per line from a file, use for /f to get the full classpath
set "CP="
for /f "usebackq delims=" %%i in ("target\cp.txt") do set "CP=%%i"

echo [2/3] Mode: dashboard (config: app-config-dashboard.txt), start on port %DEMO_PORT%, press Ctrl+C to stop...
echo [3/3] Main page: http://localhost:%DEMO_PORT%/   state: /demo/state   dashboard: http://localhost:8858
echo.
java -Ddemo.config=app-config-dashboard.txt -Dundertow.port=%DEMO_PORT% -Dundertow.host=0.0.0.0 -cp ".\target\classes;%CP%" cn.hg.aifei.sentinel.demo.DemoApp
