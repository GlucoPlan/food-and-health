#!/usr/bin/env bash
# Сделать резервную копию прямо сейчас (например, перед обновлением или переездом).
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"

systemctl start foodhealth-backup.service
echo "Последние копии в $BACKUPS_DIR:"
find "$BACKUPS_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%f\n' | sort | tail -3
