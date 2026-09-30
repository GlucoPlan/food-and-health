#!/usr/bin/env bash
# Настройка Telegram-бота (ТЗ 17.7): токен и кто чьи отчёты получает.
#
#   sudo bash server/deploy/telegram-setup.sh [--new-token]
#
# Перед запуском каждый пишет боту /start. Скрипт можно запускать повторно.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"
[[ -x $APP_DIR/venv/bin/python ]] || die "сначала установите сервер: sudo bash server/deploy/install.sh ДОМЕН"

# Права на настройки — до и после диалога, даже если он прервался: сервис читает их через группу
fix_permissions() {
    local f
    for f in "$ENV_FILE" "$TELEGRAM_CONFIG"; do
        [[ -f $f ]] || continue
        chown root:"$USER_NAME" "$f"
        chmod 640 "$f"
    done
}
fix_permissions

PROFILES=$(mktemp)
trap 'rm -f "$PROFILES"; fix_permissions' EXIT
cd "$APP_DIR"
# Базу читает только пользователь сервиса: файлы SQLite не должны стать чужими
run_as_service -m app.telegram_setup profiles > "$PROFILES"
"$APP_DIR/venv/bin/python" -m app.telegram_setup configure \
    --env-file "$ENV_FILE" --config "$TELEGRAM_CONFIG" --profiles "$PROFILES" "$@"
echo "Отчёты придут завтра в 6:00. Отправить за вчера прямо сейчас: sudo bash server/deploy/send-reports.sh"
