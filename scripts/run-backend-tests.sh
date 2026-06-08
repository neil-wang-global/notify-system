#!/bin/bash
set -e
echo "Running backend tests..."
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
./backend/gradlew -p backend test "$@"
echo "All tests passed."
