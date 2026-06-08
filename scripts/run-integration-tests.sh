#!/bin/bash
set -e
echo "Running integration tests (requires Docker)..."
cd "$(dirname "$0")/.."
RUN_DOCKER_TESTS=true ./backend/gradlew -p backend test --tests "*.IT" "$@"
echo "Integration tests passed."
