# Dan-burn-go Backend

  > 서울 주요 관광지 122곳의 **실시간 혼잡도**를 수집·분석하여, AI 기반 원인 분석과 대체 장소·교통 경로를 추천하는 마이크로서비스 백엔드

   [![CI](https://github.com/Dan-burn-go/Backend/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Dan-burn-go/Backend/actions/workflows/ci.yml)
  [![CD](https://github.com/Dan-burn-go/Backend/actions/workflows/cd.yml/badge.svg?branch=main)](https://github.com/Dan-burn-go/Backend/actions/workflows/cd.yml)

  ---

  ## System Architecture

  <img width="1187" height="665" alt="image" src="https://github.com/user-attachments/assets/97ddb59e-6afc-4a9c-8ee5-413ab43d9f45" />

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
  - 분석 결과를 RabbitMQ 역방향 이벤트로 발행, Congestion Service가 Redis 캐싱 + MySQL 저장 일원화

  ### 3. 대체지 추천 (Map Service)
  - MySQL `ST_Distance_Sphere`로 반경 2km 이내 유사 카테고리 후보 추출
  - 외부 길찾기 API로 소요시간 병렬 조회 → 가까운 순 정렬

  ### 4. 교통 경로 추천 (Mobility Service)
  - 외부 버스 API 연동, 소요시간 순 경로 정렬

  ---

  ## Trouble Shooting
  ### [RabbitMQ 기반 비동기 EDA 구조를 이용한 AI분석 기능 도입] [🔗Blog](https://velog.io/@kim138762/RabbitMQ-%EA%B8%B0%EB%B0%98-%EB%B9%84%EB%8F%99%EA%B8%B0-EDA-%EA%B5%AC%EC%A1%B0%EB%A5%BC-%EC%9D%B4%EC%9A%A9%ED%95%9C-AI%EB%B6%84%EC%84%9D-%EA%B8%B0%EB%8A%A5-%EB%8F%84%EC%9E%85)
  - **문제**: AI분석 기능 도입 과정에서 5분 주기 수집의 요청 버스트와 AI API 호출 제한의 충돌.
  - **해결**: Java와 Python 서비스를 분리하고, RabbitMQ 기반의 비동기 EDA 구조를 도입하여 시스템 간 의존성을 완전히 제거.
  - **결과**: AI API의 호출 제한 내에서 안정적인 분석 처리가 가능해졌으며, 서비스의 장애가 전파되지 않는 장애격리 달성

  ### [DLQ 도입으로 API Rate Limit 환경에서 메시지 유실 제로 달성] [🔗Blog](https://velog.io/@kim138762/DLQ-%EB%8F%84%EC%9E%85%EC%9C%BC%EB%A1%9C-API-Rate-Limit-%ED%99%98%EA%B2%BD%EC%97%90%EC%84%9C-%EB%A9%94%EC%8B%9C%EC%A7%80-%EC%9C%A0%EC%8B%A4-%EC%A0%9C%EB%A1%9C-%EB%8B%AC%EC%84%B1)
  - 문제: AI 분석 파이프라인에서 외부 API Rate Limit(429) 시 메시지 유실 발생. 쿼터 회복에 수 분 소요되어 단순 재시도로 해결 불가
  - 해결: RabbitMQ DLQ로 실패/정상 경로를 분리하여, 정상 처리를 막지 않으면서 실패 메시지의 최종 처리를 보장하는 복구 파이프라인 구현
  - 성과: 배포 후 메시지 유실 0건, DLQ 경유 복구 확인

  ### (도입 예정)Circuit Breaker 기반 장애 전파 차단 및 캐시 폴백 
  - **문제**: 서울시 공공 API 타임아웃 또는 AI API rate limit 초과 시, 연속 실패가 서비스 전체 응답 지연으로 전파
  - **해결 (예정)**: 연쇄 장애 차단 및 가용성 확보를 위한 Resilience4j 기반 Circuit Breaker 설계. 실패율 임계치에 따른 회로 개방과 Half-Open 상태를 통한 자동 복구 프로세스 구축
  - **기대 결과 (예상)**: 외부 API 장애 시에도 사용자에게 끊김 없는 데이터 제공 (가용성 확보)

  ### (도입 예정)AI 에이전트 기반 실시간 원인 분석 시스템 구축
  
  - **문제:** 정량적 수치 기반의 내부 데이터만으로는 실시간 혼잡 원인을 구체적으로 분석하는 데 데이터 한계가 존재
  - **해결:** MCP와 펑션 콜링 기반 지능형 에이전트를 설계하여 분석에 필요한 외부 맥락 데이터를 실시간으로 동적 수집 및 통합함.
  - **결과:** 내부 지표와 웹 검색 데이터를 결합해 원인 분석 정확도를 상승시킴

  ---
