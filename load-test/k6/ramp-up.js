import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

// 게이트웨이 기본 주소. dev 등 다른 환경은 BASE_URL 환경변수로 덮어쓴다.
//   k6 run -e BASE_URL=https://dev.example.com load-test/k6/ramp-up.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  // VU 1 -> 50 선형 증가 (issue #333: 로컬 회귀 감지용 기준선)
  stages: [
    { duration: '30s', target: 5 },  // 워밍업
    { duration: '5m', target: 50 },  // 선형 증가
    { duration: '1m', target: 0 },   // cooldown
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],                        // 에러율 1% 미만
    http_req_duration: ['p(95)<500'],                      // 전체 p95 500ms 미만
    'http_req_duration{endpoint:list}': ['p(95)<800'],     // 122개 목록 — 가장 무거움
    'http_req_duration{endpoint:detail}': ['p(95)<300'],   // 단일 조회 — 캐시 hit 기대
    'http_req_duration{endpoint:hourly}': ['p(95)<800'],   // 시간별 집계 쿼리
    'http_req_duration{endpoint:ranking}': ['p(95)<800'],  // 정렬+집계
  },
};

// 실제 areaCode 목록을 시드 추측 없이 런타임에 확보한다.
export function setup() {
  const res = http.get(`${BASE_URL}/api/congestion`);
  if (res.status !== 200) {
    throw new Error(
      `setup 실패: GET /api/congestion -> ${res.status}. 게이트웨이/service-congestion 기동 여부를 확인하세요.`,
    );
  }
  const codes = (res.json('data') || []).map((c) => c.areaCode).filter(Boolean);
  if (codes.length === 0) {
    throw new Error('areaCode 목록이 비어 있음 — 혼잡도 시드 데이터를 확인하세요.');
  }
  console.log(`setup 완료: areaCode ${codes.length}개 로드`);
  return { areaCodes: codes };
}

export default function (data) {
  const areaCode = data.areaCodes[Math.floor(Math.random() * data.areaCodes.length)];

  group('list', () => {
    const res = http.get(`${BASE_URL}/api/congestion`, { tags: { endpoint: 'list' } });
    check(res, { 'list status 200': (r) => r.status === 200 });
  });

  group('detail', () => {
    const res = http.get(`${BASE_URL}/api/congestion/${areaCode}`, { tags: { endpoint: 'detail' } });
    check(res, { 'detail status 200': (r) => r.status === 200 });
  });

  group('hourly', () => {
    const res = http.get(`${BASE_URL}/api/congestion/analysis/hourly/${areaCode}?days=7`, {
      tags: { endpoint: 'hourly' },
    });
    check(res, { 'hourly status 200': (r) => r.status === 200 });
  });

  group('ranking', () => {
    const res = http.get(`${BASE_URL}/api/congestion/analysis/ranking/busiest?limit=10`, {
      tags: { endpoint: 'ranking' },
    });
    check(res, { 'ranking status 200': (r) => r.status === 200 });
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'load-test/results/summary.json': JSON.stringify(data, null, 2),
  };
}
