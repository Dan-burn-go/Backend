# 007. 혼잡 원인 분석에 in-process MCP 서버와 LLM Function Calling 을 쓴다

- 날짜: 2026-06
- 상태: 채택

## 배경
혼잡도 수치만으로는 원인을 알 수 없다. 행사, 사고 같은 비정형 정보가 필요하다.

## 결정
in-process MCP 서버(`service-ai/app/ai/mcp/server.py`)에 `search_web` 도구를 두고, in-memory transport 클라이언트로 LLM Function Calling 과 연결한다.

- `search_web`: DuckDuckGo News(`ddgs`, region `kr-kr`), 상위 5건의 제목과 날짜만 반환
- max_hops=1 (LLM 왕복 최대 2회 고정), 병렬 tool call 허용
- TPD soft limit 80% 도달 시 도구를 주입하지 않고 기본 분석만 수행
- 시스템 프롬프트에 KST 오늘 날짜와 시간대×지역 매트릭스 룰을 주입

## 근거
- 별도 프로세스 없이 in-memory transport 로 붙이면 배포 단위가 늘지 않는다
- 제목과 날짜만 받으면 본문 파싱 없이 토큰을 아끼면서 최신성 판정이 된다
- 왕복 횟수를 고정하지 않으면 토큰 사용이 예측 불가능해진다
- 토큰 한도에 가까워질 때 도구를 끊는 쪽이 분석 전체를 실패시키는 것보다 낫다

## 결과/영향
- 검색 결과의 발행일 판정이 분석 품질을 좌우한다. 상대 날짜 파싱 버그를 여러 번 고쳤다
- 행사 시점 귀속 규칙(종료 후 1~2시간 퇴장 인파까지 인정, 종료시각 불명 시 시작+3시간 가정)을 프롬프트에 명시했다
- 내부 검증 시스템은 없다. Grafana Log Stream 의 `[Analysis]` 구조화 로그를 사람이 외부 LLM 으로 채점한다

## 관련
- 커밋 `5edac19`, `9d9a9c3`, `381026c`, `787f4af` (발행일·상대날짜 파싱), `ca811b9`, `ce85cf0`, `a49149b`, `5ba2f2c` (행사 귀속 규칙)
- `service-ai/CLAUDE.md`
