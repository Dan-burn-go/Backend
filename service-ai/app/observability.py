import atexit
import logging
import logging.handlers
import queue

import logging_loki
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.aio_pika import AioPikaInstrumentor
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from app.config import settings


class _TraceLogFormatter(logging.Formatter):
    """로그 레코드에 traceId/spanId를 포함하는 포맷터."""

    def format(self, record: logging.LogRecord) -> str:
        span = trace.get_current_span()
        ctx = span.get_span_context()
        if ctx and ctx.is_valid:
            record.traceId = format(ctx.trace_id, "032x")
            record.spanId = format(ctx.span_id, "016x")
        else:
            record.traceId = "0" * 32
            record.spanId = "0" * 16
        return super().format(record)


_tracer_provider: TracerProvider | None = None
_queue_listener: logging.handlers.QueueListener | None = None


def setup_observability(app) -> None:
    """OpenTelemetry tracing + Loki logging 초기화. lifespan startup에서 호출."""
    global _tracer_provider, _queue_listener

    # ── Tracing ──────────────────────────────────────────────
    resource = Resource.create({"service.name": "service-ai"})
    _tracer_provider = TracerProvider(resource=resource)

    otlp_exporter = OTLPSpanExporter(endpoint=settings.otlp_traces_url)
    _tracer_provider.add_span_processor(BatchSpanProcessor(otlp_exporter))
    trace.set_tracer_provider(_tracer_provider)

    FastAPIInstrumentor.instrument_app(app)
    AioPikaInstrumentor().instrument()

    # ── Logging ───────────────────────────────────────────────
    # 포맷: [traceId spanId] — Grafana derivedFields regex와 일치
    fmt = "%(asctime)s %(levelname)s %(name)s [%(traceId)s %(spanId)s] - %(message)s"
    formatter = _TraceLogFormatter(fmt)

    # Loki 핸들러를 QueueHandler로 감싸서 이벤트 루프 차단 방지
    class _LevelLokiHandler(logging_loki.LokiHandler):
        """로그 레벨을 Loki label에 포함시키는 핸들러."""
        def emit(self, record):
            self.emitter.build_tags(record)
            record.tags = {**getattr(record, "tags", {}), "level": record.levelname}
            super().emit(record)

    loki_handler = _LevelLokiHandler(
        url=settings.loki_url,
        tags={"app": "service-ai"},
        version="1",
    )
    loki_handler.setFormatter(formatter)

    log_queue: queue.Queue = queue.Queue(1000)
    queue_handler = logging.handlers.QueueHandler(log_queue)
    _queue_listener = logging.handlers.QueueListener(log_queue, loki_handler, respect_handler_level=True)
    _queue_listener.start()

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)

    root_logger = logging.getLogger()
    root_logger.setLevel(logging.INFO)
    root_logger.handlers.clear()
    root_logger.addHandler(console_handler)
    root_logger.addHandler(queue_handler)

    # uvicorn 로거가 root로 전파되도록 설정
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        uv_logger = logging.getLogger(name)
        uv_logger.handlers.clear()
        uv_logger.propagate = True


def shutdown_observability() -> None:
    """TracerProvider를 flush하고 종료. lifespan shutdown에서 호출."""
    if _queue_listener is not None:
        _queue_listener.stop()
    if _tracer_provider is not None:
        _tracer_provider.force_flush()
        _tracer_provider.shutdown()
