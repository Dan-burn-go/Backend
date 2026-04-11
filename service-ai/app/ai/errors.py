"""AI 호출 실패 분류용 예외 계층.

- Cerebras 429 응답: body.error.code + x-should-retry 헤더로 재시도 판정
- NonRetriableError: 재시도 금지 → 즉시 DLQ 라우팅
  · queue_exceeded
  · x-should-retry: false
- RetriableError: retry_after 초 후 재시도 가능
  · token_quota_exceeded
"""

from __future__ import annotations


class AIServiceError(Exception):
    """service-ai AI 호출 오류 베이스 클래스."""

    def __init__(self, error_code: str, message: str) -> None:
        super().__init__(f"[{error_code}] {message}")
        self.error_code = error_code
        self.message = message


class NonRetriableError(AIServiceError):
    """재시도 금지 오류 → 즉시 DLQ.

    대표 케이스
    - Cerebras queue_exceeded: Free tier 큐 포화 (재시도 시 즉시 재실패)
    - x-should-retry: false 헤더: 서버가 명시적 재시도 금지
    """


class RetriableError(AIServiceError):
    """retry_after 초 후 재시도 가능 오류.

    대표 케이스
    - Cerebras token_quota_exceeded: TPM/TPD 초과 (리셋 시간까지 대기)
    """

    def __init__(
        self,
        error_code: str,
        message: str,
        retry_after: float,
    ) -> None:
        super().__init__(error_code, message)
        self.retry_after = retry_after
