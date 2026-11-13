#!/usr/bin/env bash
# Builds and starts Simple-Image-Proof (macOS / Linux). Needs JDK 17+ and Maven.
cd "$(dirname "$0")" || exit 1

if ! command -v java > /dev/null; then
  echo "Java not found. Install JDK 17 or newer."
  exit 1
fi
if ! command -v mvn > /dev/null; then
  echo "Maven not found. Install Maven (https://maven.apache.org)."
  exit 1
fi

# First number of the version: "17.0.2" -> 17, old "1.8.0" -> 1
VERSION=$(java -version 2>&1 | head -n 1 | sed -E 's/[^"]*"([0-9]+).*/\1/')
if [ "$VERSION" -lt 17 ]; then
  echo "Java 17 or newer needed, found: $(java -version 2>&1 | head -n 1)"
  exit 1
fi

# Always run Maven: it only recompiles what changed (src, pom, resources).
# Tests are skipped so the app starts fast; run "mvn test" for them.
mvn -q -B package -Dexec.skip=true || exit 1
java -jar target/simple-image-proof-0.1.0.jar
