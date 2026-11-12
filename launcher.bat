@echo off
rem Builds (if needed) and starts Simple-Image-Proof. Requires JDK 17+ and Maven.
cd /d "%~dp0"
set JAR=target\simple-image-proof-0.1.0.jar
if not exist "%JAR%" call mvn -q -B package || exit /b 1
java -jar "%JAR%"
