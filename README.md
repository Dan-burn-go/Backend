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
  - **해결**: RabbitMQ TopicExchange(`congestion.events`)로 발행-구독 분리. `CongestionEventPublisher`가 BUSY 이벤트를 발행하고, Python Consumer가 aio-pika로 소비. `Jackson2JsonMessageConverter`로 Java↔Python 간 직렬화 통일
  - **결과**: 데이터 수집과 AI 분석의 완전한 비동기 분리, AI 서비스 장애 시에도 혼잡도 수집·서빙 무중단 보장

  ### 시간/크기 이중 윈도우 배치 처리 및 재시도 전략
  - **문제**: 퇴근 시간대 등 다수 장소가 동시 BUSY 전이 시, 건별 AI API 호출로 rate limit 초과 및 비용 폭증
  - **해결**: `BatchProcessor`에서 2초 타이머 윈도우 또는 10건 도달 시 flush하여 1회 배치 호출. 실패 시 최대 2회 재시도 후 폐기. 결과를 RabbitMQ 역방향 이벤트로 발행하여 Congestion Service에 저장 위임
  - **윈도우 시간 근거**: 2초는 사용자 체감 지연(리포트 생성 대기)과 API 효율(배치 효과) 사이의 타협점
  - **결과**: AI API 호출 최대 90% 절감, 재시도 + 폐기 전략으로 메시지 큐 적체 방지

  ### Strategy 패턴 + 조건부 빈을 활용한 외부 API 클라이언트 추상화
  - **문제**: 서울시 API 키/AI API 키가 없는 로컬 환경에서 외부 API 호출 실패로 서비스 기동 불가, 팀원의 개발 진입 장벽
  - **해결**: `SeoulApiClient` 인터페이스에 `@ConditionalOnProperty`로 API 키 존재 시 `RealClient`, 미존재 시 `StubClient` 자동 전환. Python AI 서비스도 팩토리 패턴으로 동일 구조 적용
  - **결과**: clone → docker compose up만으로 외부 API 키 없이 전체 파이프라인 테스트 가능, 새 프로바이더 추가 시 팩토리에 구현체 등록만으로 확장 (OCP 준수)

  ### 상승 엣지 감지 기반 이벤트 발행 최적화
  - **문제**: BUSY 상태가 30분 유지되면 5분 주기 수집 6회 동안 매번 AI 분석 이벤트가 중복 발행, 불필요한 API 비용 발생
  - **해결**: `CongestionStateTracker`에서 이전 상태를 Redis `multiGet` 벌크 조회(1회), Redis 미스 시 DB `IN` 쿼리 벌크 폴백(1회). 이전 ≠ BUSY → 현재 = BUSY (상승 엣지)일 때만 발행
  - **결과**: 중복 AI 분석 이벤트 제거, 122개 장소의 상태 판정을 N+1 없이 최대 2회 쿼리로 처리

  ### At-Least-Once + 멱등성 기반 Exactly-Once 정합성 구현
  - **문제**: 컨슈머 장애 시 메시지 재전송에 의한 중복 유입으로 **통계 데이터가 오염될 가능성 확인**
  - **해결**: At-Least-Once 전송 보장과 UNIQUE 제약 조건(areaCode + populationTime) 기반의 멱등성 확보로 **사실상의 Exactly-Once 정합성 구현**
  - **결과**: 장애 상황에서도 데이터 무결성을 유지하고, 중복 호출 방지를 통해 **AI API 토큰 비용 최적화**

  ### Circuit Breaker 기반 장애 전파 차단 및 캐시 폴백 (도입 예정)
  - **문제**: 서울시 공공 API 타임아웃 또는 AI API rate limit 초과 시, 연속 실패가 서비스 전체 응답 지연으로 전파
  - **해결 (예정)**: Resilience4j Circuit Breaker로 실패율 임계치 초과 시 회로 차단, API 호출 없이 Redis 캐시 데이터로 폴백 서빙. Half-Open 상태에서 재시도 후 복구 시 회로 정상화
  - **기대 결과 (예상)**: 외부 API 장애 시에도 사용자에게 끊김 없는 데이터 제공 (가용성 확보)


  ---
