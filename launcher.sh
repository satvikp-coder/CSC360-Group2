#!/usr/bin/env bash
# Build with Java 17+ and Maven; analysis itself is entirely offline.
set -eu
cd "$(dirname "$0")"
command -v java >/dev/null 2>&1 || { echo 'Java 17+ is required; install a JDK and add it to PATH.' >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo 'Maven is required; install it and add it to PATH.' >&2; exit 1; }
version=$(java -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)
major=${version%%.*}
[ "${major:-0}" -ge 17 ] 2>/dev/null || { echo 'Java 17 or later is required.' >&2; exit 1; }
mvn -q -B -DskipTests -Dexec.skip=true package
exec java -jar target/simple-image-proof-0.1.0.jar
