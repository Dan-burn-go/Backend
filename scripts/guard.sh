#!/usr/bin/env bash
# PreToolUse(Bash) 훅: 명령 어디에 있든 위험 패턴이 있으면 exit 2로 차단한다.
# settings.json의 deny는 명령 앞부분만 보므로 `cd x && rm -rf y` 같은 체인은 여기서 막는다.

# 명령을 못 읽으면(jq 없음, 입력 형식 다름) 통과시키지 않고 막는다
if ! command -v jq >/dev/null 2>&1; then
  echo "guard.sh 차단: jq 가 없어 명령을 검사할 수 없다. jq 를 설치할 것." >&2
  exit 2
fi
cmd=$(jq -r '.tool_input.command // empty') || { echo "guard.sh 차단: 훅 입력을 읽지 못했다." >&2; exit 2; }

# 차단 패턴 (확장 정규식). 프로젝트에 맞게 추가한다.
# rm 은 -r 과 -f 가 따로 오거나 순서가 바뀌어도(-fr, -r -f, --recursive --force) 잡는다
rm_word='(^|[^[:alnum:]_-])rm[[:space:]]'
rm_recursive='[[:space:]](-[[:alpha:]]*[rR][[:alpha:]]*|--recursive)([[:space:]]|$)'
rm_force='[[:space:]](-[[:alpha:]]*f[[:alpha:]]*|--force)([[:space:]]|$)'
patterns=(
  'git push.*[[:space:]](-f|--force)([[:space:]]|$)'
  'git reset --hard'
  'DROP (TABLE|DATABASE)'
  'docker system prune.*--volumes'
  'docker volume rm'
)

# rm: 명령을 ; & | 로 나눈 조각마다 rm 이 있고 재귀·강제 옵션이 둘 다 있으면 차단
while IFS= read -r part; do
  if grep -qE "$rm_word" <<<"$part" && grep -qE "$rm_recursive" <<<"$part" && grep -qE "$rm_force" <<<"$part"; then
    echo "guard.sh 차단: rm 재귀+강제 삭제. 꼭 필요하면 사용자에게 직접 실행을 요청할 것." >&2
    exit 2
  fi
done < <(tr ';&|' '\n\n\n' <<<"$cmd")

# 나머지 패턴이 하나라도 맞으면 이유를 stderr로 알리고 차단
for p in "${patterns[@]}"; do
  if grep -qiE "$p" <<<"$cmd"; then
    echo "guard.sh 차단: '$p' 패턴. 꼭 필요하면 사용자에게 직접 실행을 요청할 것." >&2
    exit 2
  fi
done
exit 0
