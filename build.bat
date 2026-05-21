@echo off
REM ============================================================
REM  GenAI JMeter Plugin — Build Script
REM  Requirements: Java 11+, Maven 3.6+
REM ============================================================

echo.
echo  GenAI JMeter Plugin - Build
echo  ============================

REM Check Maven
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo  ERROR: Maven not found in PATH.
    echo  Download from: https://maven.apache.org/download.cgi
    echo  Then add Maven bin\ to your system PATH.
    pause
    exit /b 1
)

REM Check Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo  ERROR: Java not found in PATH.
    pause
    exit /b 1
)

echo  Building plugin JAR...
call mvn clean package -q

if %errorlevel% neq 0 (
    echo  BUILD FAILED. Check errors above.
    pause
    exit /b 1
)

echo.
echo  BUILD SUCCESSFUL
echo.
echo  Plugin JAR: target\genai-jmeter-plugin-1.0.0-jmeter.jar
echo.
echo  To install in JMeter:
echo    Copy the JAR to:  JMETER_HOME\lib\ext\
echo    Restart JMeter
echo    Open Tools menu ^> GenAI Correlation Plugin
echo.

REM Ask if they want to auto-install
set /p JMETER_HOME="Enter JMeter home path to auto-install (or ENTER to skip): "
if not "%JMETER_HOME%"=="" (
    if exist "%JMETER_HOME%\lib\ext" (
        copy /Y "target\genai-jmeter-plugin-1.0.0-jmeter.jar" "%JMETER_HOME%\lib\ext\"
        echo  Installed to %JMETER_HOME%\lib\ext\
    ) else (
        echo  JMeter lib\ext not found at: %JMETER_HOME%\lib\ext
    )
)

pause
