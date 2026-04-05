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
  - 분석 결과를 RabbitMQ 역방향 이벤트로 발행, Congestion Service가 Redis 캐싱 + MySQL 저장 일원화

  ### 3. 대체지 추천 (Map Service)
  - MySQL `ST_Distance_Sphere`로 반경 2km 이내 유사 카테고리 후보 추출
  - 외부 길찾기 API로 소요시간 병렬 조회 → 가까운 순 정렬

  ### 4. 교통 경로 추천 (Mobility Service)
  - 외부 버스 API 연동, 소요시간 순 경로 정렬

  ---

  ## Trouble Shooting
  ### RabbitMQ 기반 이벤트 드리븐 서비스 간 비동기 통신
  - **문제**: 혼잡도 수집(Java) 후 AI 분석(Python)을 동기 호출하면 AI API 응답 지연이 수집 사이클을 블로킹하고, AI 서비스 장애 시 Congestion 서비스까지 연쇄 장애
  - **해결**: RabbitMQ TopicExchange를 활용한 발행-구독 모델 분리 및 Python aio-pika 기반의 비동기 메시지 소비 파이프라인 구축
  - **결과**: 데이터 수집과 AI 분석의 완전한 비동기 분리, AI 서비스 장애 시에도 혼잡도 수집·서빙 무중단 보장

  ### EDA 환경에서의 At-Least-Once + 멱등성 기반 Exactly-Once 정합성 구현
  - **문제**: RabbitMQ의 최소 한 번 전송 정책으로 인해, 컨슈머 장애 시 메시지 재전송에 의한 중복 유입으로 **통계 데이터가 오염될 가능성 확인**
  - **해결**: At-Least-Once 전송 보장과 UNIQUE 제약 조건(areaCode + populationTime) 기반의 멱등성 확보로 **사실상의 Exactly-Once 정합성 구현**
  - **결과**: 장애 상황에서도 데이터 무결성을 유지하고, 중복 호출 방지를 통해 **AI API 토큰 비용 최적화**

  ### (도입 예정)Circuit Breaker 기반 장애 전파 차단 및 캐시 폴백 
  - **문제**: 서울시 공공 API 타임아웃 또는 AI API rate limit 초과 시, 연속 실패가 서비스 전체 응답 지연으로 전파
  - **해결 (예정)**: 연쇄 장애 차단 및 가용성 확보를 위한 Resilience4j 기반 Circuit Breaker 설계. 실패율 임계치에 따른 회로 개방과 Half-Open 상태를 통한 자동 복구 프로세스 구축
  - **기대 결과 (예상)**: 외부 API 장애 시에도 사용자에게 끊김 없는 데이터 제공 (가용성 확보)

  ### (도입 예정)AI 에이전트 기반 실시간 원인 분석 시스템 구축
  
  - **문제:** 정량적 수치 기반의 내부 데이터만으로는 실시간 혼잡 원인을 구체적으로 분석하는 데 데이터 한계가 존재
  - **해결:** MCP와 펑션 콜링 기반 지능형 에이전트를 설계하여 분석에 필요한 외부 맥락 데이터를 실시간으로 동적 수집 및 통합함.
  - **결과:** 내부 지표와 웹 검색 데이터를 결합해 원인 분석 정확도를 상승시킴

  ---
