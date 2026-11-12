@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (echo Java 17 or later is required. Install a JDK and add it to PATH. & exit /b 1)
where mvn >nul 2>&1
if errorlevel 1 (echo Maven is required to build. Install Maven and add it to PATH. & exit /b 1)
for /f "tokens=3" %%v in ('java -version 2^>^&1') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~v"
for /f "tokens=1 delims=." %%v in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%v"
if not defined JAVA_MAJOR (echo Cannot determine Java version. Use JDK 17 or later. & exit /b 1)
if %JAVA_MAJOR% LSS 17 (echo Java 17 or later is required. & exit /b 1)
rem Always let Maven check source/pom changes. Startup does not execute tests.
call mvn -q -B -DskipTests -Dexec.skip=true package
if errorlevel 1 (echo Build failed. Check JDK 17 and Maven installation. & exit /b 1)
java -jar target\simple-image-proof-0.1.0.jar
exit /b %errorlevel%
