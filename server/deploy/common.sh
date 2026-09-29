# Общие пути и проверки для скриптов развёртывания. Подключается через source.
# shellcheck shell=bash
# Переменные используются в подключающих скриптах
# shellcheck disable=SC2034

APP_DIR=/opt/foodhealth
DATA_DIR=/var/lib/foodhealth
ETC_DIR=/etc/foodhealth
ENV_FILE=$ETC_DIR/env
BACKUPS_DIR=/var/backups/foodhealth
PORT=8765
SERVICE=foodhealth
USER_NAME=foodhealth

REPO_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mОшибка: %s\033[0m\n' "$*" >&2; exit 1; }

require_root() {
    [[ $EUID -eq 0 ]] || die "запустите через sudo: sudo bash $0 $*"
}

# Запрос к серверу на этой машине с ключом семьи; выводит HTTP-код
local_health() {
    local key
    key=$(sed -n 's/^FH_FAMILY_KEY=//p' "$ENV_FILE")
    curl -s -o /dev/null -w '%{http_code}' -H "X-Family-Key: $key" "http://127.0.0.1:$PORT/health"
}

wait_local_health() {
    for _ in $(seq 1 30); do
        [[ $(local_health) == 200 ]] && return 0
        sleep 1
    done
    die "сервер не отвечает на 127.0.0.1:$PORT; журнал: journalctl -u $SERVICE -n 50"
}

run_backup_tool() {
    # shellcheck disable=SC2046
    env $(grep -v '^#' "$ENV_FILE" | xargs) \
        "$APP_DIR/venv/bin/python" -m app.backup --backups-dir "$BACKUPS_DIR" "$@"
}
