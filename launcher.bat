@echo off
rem Builds and starts Simple-Image-Proof (Windows). Needs JDK 17+ and Maven.
cd /d "%~dp0"

where java >nul 2>nul || (echo Java not found. Install JDK 17 or newer. & exit /b 1)
where mvn >nul 2>nul || (echo Maven not found. Install Maven. & exit /b 1)

rem First number of the version: "17.0.2" -> 17, old "1.8.0" -> 1
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JVER=%%~v
for /f "delims=." %%m in ("%JVER%") do set JMAJOR=%%m
if %JMAJOR% LSS 17 (echo Java 17 or newer needed, found %JVER% & exit /b 1)

rem Always run Maven: it only recompiles what changed. Tests are skipped for a fast start.
call mvn -q -B package -Dexec.skip=true || exit /b 1
java -jar target\simple-image-proof-0.1.0.jar
