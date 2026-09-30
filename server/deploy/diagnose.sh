#!/usr/bin/env bash
# Что сервер видит в базе для отчётов (разбор «пустых» отчётов). Только чтение, без содержимого записей.
#
#   sudo bash server/deploy/diagnose.sh [--date ГГГГ-ММ-ДД]
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"

cd "$APP_DIR"
run_as_service -m app.diagnose "$@"
