#!/usr/bin/env bash
# Архив для переезда: свежая копия данных, ключ семьи и настройки Telegram.
#
#   sudo bash server/deploy/export.sh [папка-для-архива]
#
# Архив содержит ключ семьи и токен бота — храните и передавайте его аккуратно.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"

OUT_DIR=${1:-$PWD}
OUT="$OUT_DIR/foodhealth-export-$(date +%Y%m%d-%H%M%S).tar.gz"
cd "$APP_DIR"
run_backup_tool export --env "$ENV_FILE" --out "$OUT"
# Архив отдаём тому, кто запустил sudo, чтобы его можно было скопировать scp
[[ -n ${SUDO_USER:-} ]] && chown "$SUDO_USER" "$OUT"
echo "Перенесите архив на новый сервер и выполните там:"
echo "  sudo bash server/deploy/install.sh ДОМЕН"
echo "  sudo bash server/deploy/restore.sh $(basename "$OUT")"
