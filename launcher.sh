#!/usr/bin/env bash
# Builds (if needed) and starts Simple-Image-Proof. Requires JDK 17+ and Maven.
set -e
cd "$(dirname "$0")"
JAR=target/simple-image-proof-0.1.0.jar
if [ ! -f "$JAR" ] || [ -n "$(find src -newer "$JAR" -name '*.java')" ]; then
  mvn -q -B package
fi
exec java -jar "$JAR"
