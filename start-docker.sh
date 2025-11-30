#!/bin/bash

# Script to start the application in Docker from scratch

echo "=== Stopping existing containers ==="
docker-compose down -v

echo ""
echo "=== Building and starting containers ==="
docker-compose up --build -d

echo ""
echo "=== Waiting for services to start ==="
sleep 10

echo ""
echo "=== Container status ==="
docker-compose ps

echo ""
echo "=== Application logs (last 20 lines) ==="
docker-compose logs --tail=20 app

echo ""
echo "=== PostgreSQL logs (last 10 lines) ==="
docker-compose logs --tail=10 postgres

echo ""
echo "=== Application should be available at http://localhost:8080 ==="
echo "=== To view logs in real-time, run: docker-compose logs -f app ==="

