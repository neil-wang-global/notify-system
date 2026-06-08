#!/bin/bash
# Usage: ./scripts/run-one-test.sh com.example.notify.infrastructure.redis.RedisStrategiesTest
set -e
cd "$(dirname "$0")/.."
./backend/gradlew -p backend test --tests "$1"
