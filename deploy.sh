#!/bin/bash
set -e

echo "========================================="
echo " Dan-burn-go Backend 순차 빌드 & 배포"
echo "========================================="

# 인프라 먼저 실행
echo "[1/7] 인프라 서비스 실행 (MySQL, Redis, Observability)..."
docker-compose up -d mysql redis observability

# 서비스 순차 빌드 (메모리 경합 방지)
echo "[2/7] service-ai 빌드..."
docker-compose build service-ai

echo "[3/7] service-congestion 빌드..."
docker-compose build service-congestion

echo "[4/7] service-map 빌드..."
docker-compose build service-map

echo "[5/7] service-transport 빌드..."
docker-compose build service-transport

echo "[6/7] service-gateway 빌드..."
docker-compose build service-gateway

# 전체 서비스 실행
echo "[7/7] 전체 서비스 실행..."
docker-compose up -d

echo ""
echo "========================================="
echo " 배포 완료! 컨테이너 상태:"
echo "========================================="
docker-compose ps
