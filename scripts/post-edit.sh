#!/usr/bin/env bash
# PostToolUse(Edit|Write) 훅: 수정된 파일을 포맷하고 린트한다.
# 린트가 실패하면 exit 2로 에러를 Claude에게 돌려줘 바로 고치게 한다.
file=$(jq -r '.tool_input.file_path // empty')
[ -f "$file" ] || exit 0

# 확장자별 포맷 + 린트 (프로젝트 스택에 맞게 채운다)
case "$file" in
  # 포맷·린트 도구가 설정되면 확장자별 case 를 여기에 추가한다
  *) ;;
esac
exit 0
