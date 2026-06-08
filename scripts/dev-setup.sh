#!/bin/bash
# dev-setup.sh — start infrastructure services for local development.
# Starts PostgreSQL primary/replica, Redis Cluster, and Kafka — without backend or frontend.
#
# Usage: ./scripts/dev-setup.sh

set -euo pipefail

echo "Starting infrastructure services..."
docker compose up -d postgres-primary postgres-replica \
  redis-node-1 redis-node-2 redis-node-3 redis-cluster-init \
  zookeeper kafka kafka-topics

echo ""
echo "Waiting for services to be ready..."
sleep 10
echo ""

echo "Infrastructure ready."
echo "  PG primary: localhost:5432  (write)"
echo "  PG replica: localhost:5433  (read)"
echo "  Redis:      localhost:6379-6381 (3-node cluster)"
echo "  Kafka:      localhost:9092"
echo ""
echo "Topics: user-operation-events, notification-events, user-operation-events-dlt, notification-events-dlt"
echo ""
echo "Run backend locally with:"
echo "  NOTIFY_DB_ROUTING_ENABLED=true \\"
echo "  NOTIFY_DB_WRITE_URL=jdbc:postgresql://localhost:5432/notify \\"
echo "  NOTIFY_DB_READ_URL=jdbc:postgresql://localhost:5433/notify \\"
echo "  NOTIFY_DB_USERNAME=notify NOTIFY_DB_PASSWORD=notify \\"
echo "  NOTIFY_KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \\"
echo "  REDIS_CLUSTER_NODES=localhost:6379,localhost:6380,localhost:6381 \\"
echo "  ./gradlew -p backend bootRun"
echo ""
echo "Run frontend locally with:"
echo "  cd frontend && VITE_API_BASE_URL=http://localhost:8080 npm run dev"
