"""app.ai.errors 예외 계층 테스트."""

from __future__ import annotations

import pytest

from app.ai.errors import AIServiceError, NonRetriableError, RetriableError


def test_non_retriable_error_attributes():
    err = NonRetriableError(error_code="queue_exceeded", message="queue full")
    assert err.error_code == "queue_exceeded"
    assert err.message == "queue full"
    assert "queue_exceeded" in str(err)
    assert isinstance(err, AIServiceError)
    assert isinstance(err, Exception)


def test_retriable_error_attributes():
    err = RetriableError(
        error_code="token_quota_exceeded",
        message="tpm exceeded",
        retry_after=12.5,
    )
    assert err.error_code == "token_quota_exceeded"
    assert err.retry_after == 12.5
    assert isinstance(err, AIServiceError)
    assert isinstance(err, Exception)


def test_errors_can_be_raised_and_caught():
    with pytest.raises(NonRetriableError) as info:
        raise NonRetriableError(error_code="x", message="y")
    assert info.value.error_code == "x"

    with pytest.raises(RetriableError) as info2:
        raise RetriableError(error_code="a", message="b", retry_after=1.0)
    assert info2.value.retry_after == 1.0
