#!/usr/bin/env bash
# Анализ Claude прямо сейчас (ТЗ 17.9): выгрузка и Claude Code по каждому, как ночью.
# Результат виден в приложении на вкладке «Анализ»; в Telegram уйдёт с утренними отчётами.
#
#   sudo bash server/deploy/analysis-now.sh [--date ГГГГ-ММ-ДД]
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"
[[ -f /etc/systemd/system/foodhealth-analysis.service ]] \
    || die "анализ не включён: install.sh не нашёл Claude Code у пользователя $ANALYSIS_USER"

cd "$APP_DIR"
run_as_service -m app.analysis export "$@"
say "Claude Code (по каждому до 15 минут)"
systemctl start foodhealth-analysis.service || true
journalctl -u foodhealth-analysis -n 20 --no-pager -o cat
