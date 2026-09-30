#!/usr/bin/env bash
# Отправить отчёты в Telegram сейчас (ТЗ 17.7). Уже отправленное повторно не уходит.
#
#   sudo bash server/deploy/send-reports.sh [--date ГГГГ-ММ-ДД] [--force]
#
# --force — отправить заново уже отправленные (например, после поздних записей).
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"

cd "$APP_DIR"
run_as_service -m app.send_reports "$@"
