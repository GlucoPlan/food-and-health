#!/usr/bin/env bash
# Описание релиза: сообщения всех коммитов с прошлого релиза (тег v*) до $1,
# по порядку, без служебных коммитов «ci: …» и строк Co-Authored-By.
set -euo pipefail

HEAD_SHA="${1:-HEAD}"
PREV_TAG=$(git describe --tags --abbrev=0 --match 'v*' "$HEAD_SHA" 2>/dev/null || true)
RANGE="${PREV_TAG:+$PREV_TAG..}$HEAD_SHA"

OUT=""
for sha in $(git log --reverse --no-merges --format=%H "$RANGE"); do
  subject=$(git log -1 --format=%s "$sha")
  case "$subject" in ci:*) continue ;; esac
  msg=$(git log -1 --format=%B "$sha" | grep -vi '^co-authored-by:' || true)
  # $(...) срезает хвостовые пустые строки, между коммитами — одна пустая
  OUT+="${OUT:+$'\n\n'}$msg"
done

echo "${OUT:-Без описания изменений}"
