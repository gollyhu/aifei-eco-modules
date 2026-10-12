@echo off
setlocal
cd /d "%~dp0"

rem Sentinel dashboard: port must match sentinel.dashboard.server in app-config-dashboard.txt (127.0.0.1:8858)
set "JAR=sentinel-dashboard-1.8.10.jar"
set "PORT=8858"

if not exist "%JAR%" (
    echo Dashboard jar not found: %JAR%
    echo Put sentinel-dashboard-*.jar next to this script, or update JAR above.
    exit /b 1
)

netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul 2>nul
if not errorlevel 1 (
    echo Dashboard already running on port %PORT%: http://localhost:%PORT%
    exit /b 0
)

echo Starting Sentinel dashboard on port %PORT% (account: sentinel / sentinel)
echo Open http://localhost:%PORT% , press Ctrl+C to stop...
echo.

java -Dserver.port=%PORT% -jar "%JAR%"
