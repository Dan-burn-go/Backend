#!/usr/bin/env bash
# docs/requirements(ERD) 점검: 기준마다 상태 칸이 정해진 값인지, 검증 칸의 테스트 참조가 실제로 있는지 본다.
# 검증 칸 참조 형식: `테스트클래스.메서드` (Java/Kotlin/TS 등) 또는 `경로::테스트이름` (pytest, Go 는 `foo_test.go::TestBar`)
# 상태가 `자동` 이면 참조가 하나 이상 있어야 한다. README.md 는 작성법·예시라 검사하지 않는다
set -uo pipefail
cd "$(dirname "$0")/.."

dir=docs/requirements
[ -d "$dir" ] || exit 0
test_dirs=(service-common/src/test service-congestion/src/test service-map/src/test service-mobility/src/test service-gateway/src/test service-ai/tests)
statuses='자동|자동 \(환경변수 있을 때\)|수동|미구현|일부 미구현|미충족|미정|해당 없음'
fail=0

while IFS= read -r line; do
  file=${line%%:*}
  row=${line#*:}
  id=$(awk -F'|' '{gsub(/ /, "", $2); print $2}' <<<"$row")
  verify=$(awk -F'|' '{print $4}' <<<"$row")
  status=$(awk -F'|' '{s = $(NF - 1); gsub(/^ +| +$/, "", s); print s}' <<<"$row")

  # 상태 칸
  if ! grep -qxE "$statuses" <<<"$status"; then
    echo "$file $id: 상태 칸이 비었거나 모르는 값 ($status)" >&2
    fail=1
  fi

  # 자동인데 테스트 참조가 없으면 무엇이 확인하는지 알 수 없다
  if [[ "$status" == 자동* ]] && ! grep -qE '`[A-Z][A-Za-z0-9_]*\.[a-z_][A-Za-z0-9_]*`|`[A-Za-z0-9_./-]+::[A-Za-z0-9_]+`' <<<"$verify"; then
    echo "$file $id: 상태가 자동인데 검증 칸에 테스트클래스.메서드 또는 경로::이름 이 없다" >&2
    fail=1
  fi

  # `클래스.메서드`: 테스트 폴더에 그 이름의 파일이 있고 메서드 이름이 들어 있어야 한다
  # README.md 같은 파일 이름은 테스트 참조가 아니다
  for ref in $(grep -oE '`[A-Z][A-Za-z0-9_]*\.[a-z_][A-Za-z0-9_]*`' <<<"$verify" | tr -d '`' \
      | grep -vE '\.(md|json|ya?ml|txt|java|kt|py|ts|tsx|js|go|rs|sh|xml|csv|pdf|png|hwp|hwpx)$'); do
    cls=${ref%%.*}
    method=${ref#*.}
    src=$(find "${test_dirs[@]}" -name "$cls.*" -type f 2>/dev/null | head -1)
    if [ -z "$src" ] || ! grep -qw "$method" "$src"; then
      echo "$file $id: 없는 테스트 $ref" >&2
      fail=1
    fi
  done

  # `경로::테스트이름`
  for ref in $(grep -oE '`[A-Za-z0-9_./-]+::[A-Za-z0-9_]+`' <<<"$verify" | tr -d '`'); do
    path=${ref%%::*}
    name=${ref#*::}
    if [ ! -f "$path" ] || ! grep -qw "$name" "$path"; then
      echo "$file $id: 없는 테스트 $ref" >&2
      fail=1
    fi
  done
done < <(find "$dir" -name '*.md' ! -name README.md -exec grep -HE '^\| [A-Z]{2,4}-M?[0-9]+ \|' {} +)

# ID 형식이 달라 위에서 검사하지 못한 행 (머리 줄, 구분 줄 제외)
while IFS= read -r line; do
  echo "${line%%:*}: ID 형식이 '영문2~4자-번호' 가 아니라 검사하지 못한 행: ${line#*:}" >&2
  fail=1
done < <(find "$dir" -name '*.md' ! -name README.md -exec grep -HE '^\|' {} + \
  | grep -vE ':\| (ID|파일) \||:\|[-| ]+\|$|:\| [A-Z]{2,4}-M?[0-9]+ \|')

[ "$fail" -eq 0 ] && echo "docs/requirements 점검 통과"
exit "$fail"
