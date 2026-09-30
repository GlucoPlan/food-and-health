#!/usr/bin/env bash
# Восстановление данных сервера.
#
#   sudo bash server/deploy/restore.sh АРХИВ.tar.gz     — переезд: данные, ключ семьи, настройки Telegram и отчётов
#   sudo bash server/deploy/restore.sh /var/backups/foodhealth/2026-09-29_033000   — откат на копию
#
# Перед восстановлением текущие данные сохраняются отдельной копией.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"
require_root "$@"

SOURCE=${1:-}
[[ -n $SOURCE && -e $SOURCE ]] || die "укажите архив переезда или папку копии: sudo bash $0 ПУТЬ"
SOURCE=$(realpath "$SOURCE")
[[ -x $APP_DIR/venv/bin/python ]] || die "сначала установите сервер: sudo bash server/deploy/install.sh ДОМЕН"

if [[ -f $DATA_DIR/fh.db ]]; then
    say "Копия текущих данных перед восстановлением"
    systemctl start foodhealth-backup.service
fi

say "Восстановление"
systemctl stop "$SERVICE"
cd "$APP_DIR"
if [[ -f $SOURCE ]]; then
    run_backup_tool --data-dir "$DATA_DIR" restore "$SOURCE" --env-out "$ENV_FILE"
    chown root:"$USER_NAME" "$ENV_FILE"
    chmod 640 "$ENV_FILE"
    # Настройки Telegram и отчётов переезжают вместе с ключом семьи и токеном
    for f in "$TELEGRAM_CONFIG" "$REPORTS_CONFIG"; do
        if [[ -f $f ]]; then
            chown root:"$USER_NAME" "$f"
            chmod 640 "$f"
        fi
    done
else
    run_backup_tool --data-dir "$DATA_DIR" restore "$SOURCE"
fi
chown -R "$USER_NAME":"$USER_NAME" "$DATA_DIR"
systemctl start "$SERVICE"
wait_local_health
echo "Готово: сервер запущен с восстановленными данными"
