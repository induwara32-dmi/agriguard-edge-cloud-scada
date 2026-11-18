@echo off
chcp 65001 > nul
echo ==============================================================================
echo   AgriGuard RT-SCADA: Multi-Zone Plantation Climate ^& Irrigation Controller
echo   Industrial Edge Computing Platform for Protected Agriculture (Java 17+)
echo ==============================================================================
echo.

if not exist bin mkdir bin
if not exist logs mkdir logs

echo [BUILD] Compiling edge SCADA components into bin\...
javac -encoding UTF-8 -d bin src\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed! Ensure JDK 17 or later is installed and on PATH.
    pause
    exit /b %errorlevel%
)

echo [LAUNCH] Starting AgriGuard RT-SCADA Edge Controller...
echo ------------------------------------------------------------------------------
java -Dfile.encoding=UTF-8 -cp bin Main %*
if %errorlevel% neq 0 pause
