"""app.rabbitmq.dedup — IdempotencyGate + BatchProcessor 중복 요청 방지 테스트."""

from __future__ import annotations

import pytest
from redis.exceptions import RedisError

from app.models.schemas import AnalysisResult, CongestionEvent
from app.rabbitmq.batch import BatchProcessor
from app.rabbitmq.dedup import IdempotencyGate


class FakeRedis:
    """redis.asyncio.Redis 최소 더미 (SET NX EX / DELETE / 소유자 확인 해제 EVAL)."""

    def __init__(self) -> None:
        self.store: dict[str, str] = {}

    def expire_now(self, key: str) -> None:
        # TTL 만료 시뮬레이션
        self.store.pop(key, None)

    async def eval(self, script, numkeys, key, token):
        # 해제 Lua 와 같은 의미: 값이 내 토큰일 때만 DEL
        if self.store.get(key) == token:
            del self.store[key]
            return 1
        return 0

    async def set(self, key, value, nx=False, ex=None):
        if nx and key in self.store:
            return None
        self.store[key] = value
        return True

    async def delete(self, key):
        return 1 if self.store.pop(key, None) is not None else 0


class FailingRedis:
    """모든 명령에서 RedisError → fail-open 검증용."""

    async def set(self, *a, **k):
        raise RedisError("boom")

    async def delete(self, *a, **k):
        raise RedisError("boom")

    async def eval(self, *a, **k):
        raise RedisError("boom")


class FakeMessage:
    def __init__(self, tag: int) -> None:
        self.tag = tag
        self.acked = False
        self.nacked = False
        self.nack_requeue: bool | None = None

    async def ack(self) -> None:
        self.acked = True

    async def nack(self, requeue: bool = True) -> None:
        self.nacked = True
        self.nack_requeue = requeue


class FakePublisher:
    def __init__(self) -> None:
        self.published: list[AnalysisResult] = []

    async def publish_all(self, results: list[AnalysisResult]) -> None:
        self.published.extend(results)


class FakeAnalyzer:
    def __init__(self, behaviour: str = "ok") -> None:
        self.behaviour = behaviour
        self.calls = 0
        self.seen_events: list[list[CongestionEvent]] = []

    async def analyze(self, events, *, mode="normal"):
        self.calls += 1
        self.seen_events.append(list(events))
        if self.behaviour == "boom":
            raise RuntimeError("network boom")
        return [
            AnalysisResult(
                area_name=e.area_name,
                area_code=e.area_code,
                congestion_level=e.congestion_level,
                analysis_message="ok",
                population_time=e.population_time,
            )
            for e in events
        ]


def _event(code: str, time: str = "2026-04-11 12:00") -> CongestionEvent:
    return CongestionEvent(
        area_name=f"area-{code}",
        area_code=code,
        congestion_level="BUSY",
        max_people_count=100,
        population_time=time,
    )


def _gate(redis) -> IdempotencyGate:
    return IdempotencyGate(redis, key_prefix="ai:dedup", ttl_seconds=300)


# ── IdempotencyGate 단위 ──

@pytest.mark.asyncio
async def test_acquire_first_true_second_false():
    gate = _gate(FakeRedis())
    key = gate.key_for(_event("A"))
    assert await gate.acquire(key)           # 첫 선점 성공 (토큰)
    assert await gate.acquire(key) is None   # 이미 존재 → 차단


@pytest.mark.asyncio
async def test_release_allows_reacquire():
    redis = FakeRedis()
    gate = _gate(redis)
    key = gate.key_for(_event("A"))
    token = await gate.acquire(key)
    await gate.release(key, token)
    assert await gate.acquire(key)           # 해제 후 재선점 가능


@pytest.mark.asyncio
async def test_key_matches_db_unique_area_time():
    gate = _gate(FakeRedis())
    # 같은 (area, time) → 동일 키 / time 다르면 다른 키
    assert gate.key_for(_event("A", "T1")) == gate.key_for(_event("A", "T1"))
    assert gate.key_for(_event("A", "T1")) != gate.key_for(_event("A", "T2"))
    assert gate.key_for(_event("A", "T1")) != gate.key_for(_event("B", "T1"))


@pytest.mark.asyncio
async def test_redis_failure_fails_open():
    gate = _gate(FailingRedis())
    key = gate.key_for(_event("A"))
    token = await gate.acquire(key)
    assert token                              # 장애 시 통과 (LLM 호출 진행)
    await gate.release(key, token)            # 예외 삼킴 (raise 없음)


# ── BatchProcessor 통합 ──

@pytest.mark.asyncio
async def test_duplicate_redelivery_skips_llm():
    """같은 이벤트 재전달 시 두 번째는 LLM 호출 없이 ack."""
    redis = FakeRedis()
    analyzer = FakeAnalyzer("ok")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher, gate=_gate(redis))  # type: ignore[arg-type]

    msg1 = FakeMessage(1)
    await bp._process([(_event("A"), msg1)])
    assert msg1.acked is True
    assert analyzer.calls == 1

    # 동일 (area, time) 재전달
    msg2 = FakeMessage(2)
    await bp._process([(_event("A"), msg2)])
    assert msg2.acked is True        # 중복도 ack (재큐 방지)
    assert msg2.nacked is False
    assert analyzer.calls == 1       # LLM 재호출 없음 → 토큰 절약
    assert len(publisher.published) == 1


@pytest.mark.asyncio
async def test_duplicate_within_single_batch_deduped():
    """한 배치 안에 같은 키가 둘이면 첫 건만 분석."""
    analyzer = FakeAnalyzer("ok")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher, gate=_gate(FakeRedis()))  # type: ignore[arg-type]

    msg1, msg2 = FakeMessage(1), FakeMessage(2)
    await bp._process([(_event("A"), msg1), (_event("A"), msg2)])

    assert msg1.acked and msg2.acked
    assert analyzer.calls == 1
    assert len(analyzer.seen_events[0]) == 1   # fresh 1건만 LLM 투입


@pytest.mark.asyncio
async def test_failure_releases_marker_for_reprocess():
    """분석 실패로 DLQ 이동 시 마커 해제 → 재처리가 다시 분석 가능."""
    redis = FakeRedis()
    gate = _gate(redis)

    boom = FakeAnalyzer("boom")
    bp_fail = BatchProcessor(boom, FakePublisher(), gate=gate)  # type: ignore[arg-type]
    msg1 = FakeMessage(1)
    await bp_fail._process([(_event("A"), msg1)])
    assert msg1.nacked is True and msg1.nack_requeue is False   # DLQ 라우팅
    assert redis.store == {}                                    # 마커 해제됨

    # DLQ 재처리(같은 이벤트 재유입) → 마커 없으므로 다시 분석
    ok = FakeAnalyzer("ok")
    bp_ok = BatchProcessor(ok, FakePublisher(), gate=gate)  # type: ignore[arg-type]
    msg2 = FakeMessage(2)
    await bp_ok._process([(_event("A"), msg2)])
    assert msg2.acked is True
    assert ok.calls == 1


@pytest.mark.asyncio
async def test_no_gate_processes_all_as_fresh():
    """gate=None 이면 기존 동작 (전건 분석)."""
    analyzer = FakeAnalyzer("ok")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    msg1, msg2 = FakeMessage(1), FakeMessage(2)
    await bp._process([(_event("A"), msg1), (_event("A"), msg2)])

    assert msg1.acked and msg2.acked
    assert len(analyzer.seen_events[0]) == 2   # 게이트 없음 → 둘 다 fresh


# ── 락 소유자 확인 해제 (TTL 만료 후 남의 락 삭제 방지) ──

@pytest.mark.asyncio
async def test_release_after_ttl_expiry_keeps_other_owner_lock():
    """A 처리 중 TTL 만료 → B 선점 → A 해제가 B 의 락을 지우지 않는다."""
    redis = FakeRedis()
    gate = _gate(redis)
    key = gate.key_for(_event("A"))

    token_a = await gate.acquire(key)
    redis.expire_now(key)                     # A 처리가 TTL(300s) 초과
    token_b = await gate.acquire(key)         # 재전달을 B 가 선점
    assert token_a and token_b and token_a != token_b

    await gate.release(key, token_a)          # A 실패 → 해제 시도
    assert redis.store[key] == token_b        # B 의 락 유지


@pytest.mark.asyncio
async def test_ttl_overrun_then_failure_does_not_cause_extra_llm_call():
    """A 가 TTL 을 넘겨 실패해도 B 의 마커가 남아 이후 중복 N건은 LLM 을 호출하지 않는다."""
    redis = FakeRedis()
    gate = _gate(redis)
    key = gate.key_for(_event("A"))

    class SlowFailAnalyzer(FakeAnalyzer):
        async def analyze(self, events, *, mode="normal"):
            self.calls += 1
            # A 분석 중 TTL 만료 + B 가 같은 이벤트를 선점·성공
            redis.expire_now(key)
            await bp_b._process([(_event("A"), FakeMessage(99))])
            raise RuntimeError("A 지연 후 실패")

    ok = FakeAnalyzer("ok")
    bp_b = BatchProcessor(ok, FakePublisher(), gate=gate)  # type: ignore[arg-type]
    slow = SlowFailAnalyzer()
    bp_a = BatchProcessor(slow, FakePublisher(), gate=gate)  # type: ignore[arg-type]

    await bp_a._process([(_event("A"), FakeMessage(1))])
    assert ok.calls == 1

    # 이후 같은 이벤트 중복 10건 → 모두 스킵
    for i in range(10):
        await bp_b._process([(_event("A"), FakeMessage(100 + i))])
    assert ok.calls == 1


@pytest.mark.asyncio
async def test_n_duplicate_events_call_llm_once():
    """같은 (area, time) 이벤트 N건 → LLM 호출 1건."""
    analyzer = FakeAnalyzer("ok")
    bp = BatchProcessor(analyzer, FakePublisher(), gate=_gate(FakeRedis()))  # type: ignore[arg-type]

    await bp._process([(_event("A"), FakeMessage(i)) for i in range(5)])   # 한 배치 안 중복
    for i in range(5, 20):
        await bp._process([(_event("A"), FakeMessage(i))])                  # 재전달 중복
    assert analyzer.calls == 1
