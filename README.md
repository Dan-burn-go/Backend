# Dan-burn-go Backend

  > 서울 주요 관광지 122곳의 **실시간 혼잡도**를 수집·분석하여, AI 기반 원인 분석과 대체 장소·교통 경로를 추천하는 마이크로서비스 백엔드

   [![CI](https://github.com/Dan-burn-go/Backend/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Dan-burn-go/Backend/actions/workflows/ci.yml)
  [![CD](https://github.com/Dan-burn-go/Backend/actions/workflows/cd.yml/badge.svg?branch=main)](https://github.com/Dan-burn-go/Backend/actions/workflows/cd.yml)

  ---

  ## System Architecture

  <img width="2152" alt="System Architecture" src="https://github.com/user-attachments/assets/2d90b900-06eb-4073-8896-4b7b9ec20e0d" />

  ---

  ## Tech Stack

  ![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.4.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
  ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud_2024.0.2-6DB33F?style=flat-square&logo=spring&logoColor=white)
  ![Python](https://img.shields.io/badge/Python_3.12-3776AB?style=flat-square&logo=python&logoColor=white)
  ![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=flat-square&logo=fastapi&logoColor=white)
  ![MySQL](https://img.shields.io/badge/MySQL_8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)
  ![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=flat-square&logo=redis&logoColor=white)
  ![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=flat-square&logo=rabbitmq&logoColor=white)
  ![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=flat-square&logo=docker&logoColor=white)
  ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
  ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)
  ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
  ![Swagger](https://img.shields.io/badge/Swagger_UI-85EA2D?style=flat-square&logo=swagger&logoColor=black)

  ---

  ## Key Features

  ### 1. 실시간 혼잡도 수집 파이프라인
  - 서울시 공공 API에서 **5분 주기**로 122개 장소 혼잡도 수집
  - Redis 파이프라인 배치 저장 (15분 TTL) + MySQL 비동기 이력 저장 (`@Async`)
  - 매일 03:00 크론으로 7일 초과 데이터 자동 정리
  - API Key 미설정 시 Stub 클라이언트로 더미 데이터 제공 (로컬 개발 지원)

  ### 2. EDA 기반 AI 혼잡 원인 분석
  - 혼잡도가 **BUSY로 상승 전이**될 때만 이벤트 발행 (상승 엣지 감지)
  - `congestion.events` Topic Exchange → `congestion.busy` Routing Key
  - AI Service에서 **10초 배치 윈도우**로 메시지를 모아 Cerebras Cloud API 1회 호출 (토큰 절약)
  - 분석 리포트를 Redis(TTL 4시간) + MySQL에 저장, Congestion Service API로 조회

  ### 3. 대체지 추천 (Map Service)
  - MySQL `ST_Distance_Sphere`로 반경 2km 이내 유사 카테고리 후보 추출
  - 외부 길찾기 API로 소요시간 병렬 조회 → 가까운 순 정렬

  ### 4. 교통 경로 추천 (Mobility Service)
  - 외부 버스 API 연동, 소요시간 순 경로 정렬

  ---

  ## Trouble Shooting


  - **문제**:
  - **해결**:
  - **결과**:
  ---
