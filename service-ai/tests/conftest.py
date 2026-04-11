"""pytest 공용 설정.

- service-ai 디렉터리를 sys.path 에 추가 → app.* 임포트 가능
- RABBITMQ_URL 기본값 주입 → env 의존 Settings 로드 보장
- TIKTOKEN_DISABLE=1 → 오프라인 환경 BPE 다운로드 차단
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

# service-ai/ 디렉터리 → sys.path 등록
SERVICE_AI_ROOT = Path(__file__).resolve().parent.parent
if str(SERVICE_AI_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_AI_ROOT))

# Settings(rabbitmq_url: str) 필수 필드 → 더미 값 주입
os.environ.setdefault("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")
os.environ.setdefault("AI_PROVIDER", "stub")
# tiktoken 네트워크 다운로드 무한 대기 방지 → 휴리스틱 폴백 강제
os.environ.setdefault("TIKTOKEN_DISABLE", "1")
