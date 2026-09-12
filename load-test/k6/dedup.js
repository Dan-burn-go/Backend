// Redis 분산락 dedup 동시성 검증
// 같은 (area_code, population_time) 키를 N개 VU가 "동시에" 발행 →
// RabbitMQ가 2개 팟으로 분산 소비 → SETNX 레이스 → LLM 호출은 딱 1회여야 함.
//
// 실행 (사전: redis FLUSHDB):
//   k6 run load-test/k6/dedup.js
//   k6 run -e N=50 -e POP_TIME="2026-07-06 16:00" load-test/k6/dedup.js
//
// 판정: teardown이 WireMock Admin API로 실제 LLM HTTP 호출 수를 조회해 PASS/FAIL 출력.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const PUBLISH_URL = __ENV.PUBLISH_URL || 'http://localhost:8085/test/publish';
const WIREMOCK_ADMIN = __ENV.WIREMOCK_ADMIN || 'http://localhost:8089/__admin';
const AREA_CODE = __ENV.AREA_CODE || 'POI-DEDUP';
const POP_TIME = __ENV.POP_TIME || '2026-07-06 15:00';
const N = Number(__ENV.N || 20);
const WAIT_SEC = Number(__ENV.WAIT_SEC || 12);

export const options = {
  scenarios: {
    // per-vu-iterations: N개 VU가 동시에 시작해 각 1회 발행 → 거의 같은 ms에 폭주
    burst: { executor: 'per-vu-iterations', vus: N, iterations: 1, maxDuration: '30s' },
  },
};

const published = new Counter('published_events');

export function setup() {
  // 이전 실행 잔여 요청 로그 제거 (WireMock 카운터 0으로)
  http.del(`${WIREMOCK_ADMIN}/requests`);
  return { area: AREA_CODE, time: POP_TIME };
}

export default function () {
  const url = `${PUBLISH_URL}?area_code=${encodeURIComponent(AREA_CODE)}` +
    `&population_time=${encodeURIComponent(POP_TIME)}`;
  const res = http.post(url);
  check(res, { 'publish 200': (r) => r.status === 200 });
  published.add(1);
}

export function teardown() {
  // 배치(BATCH_MAX_SIZE=1) 소비 + 처리 완료 대기
  sleep(WAIT_SEC);
  const body = JSON.stringify({ method: 'POST', urlPathPattern: '.*/chat/completions' });
  const res = http.post(`${WIREMOCK_ADMIN}/requests/count`, body, {
    headers: { 'Content-Type': 'application/json' },
  });
  const count = res.json('count');
  const savedPct = Math.round((1 - 1 / N) * 100);
  console.log('\n===== Redis 분산락 dedup 검증 =====');
  console.log(`유입(발행) 이벤트 : ${N}  (동일 키 ${AREA_CODE} / ${POP_TIME})`);
  console.log(`실제 LLM 호출     : ${count}  (WireMock 실측)`);
  if (count === 1) {
    console.log(`✅ PASS — 분산락 동작: 호출 1회, ${savedPct}% 절감`);
  } else {
    console.log(`❌ FAIL — LLM ${count}회 호출 (락 미동작 / Redis 미주입 / 마커 잔여 의심)`);
  }
  console.log('===================================\n');
}

export function handleSummary(data) {
  return {
    'load-test/results/dedup-summary.json': JSON.stringify(data, null, 2),
    stdout: `published=${data.metrics.published_events?.values?.count ?? 0}\n`,
  };
}
