#!/usr/bin/env bash
# Установка и обновление сервера «Еда и здоровье».
#
#   sudo bash server/deploy/install.sh ДОМЕН
#
# Повторный запуск обновляет код и зависимости; ключ семьи и данные не трогает.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "$0")/common.sh"

DOMAIN=${1:-}
[[ -n $DOMAIN ]] || die "укажите домен: sudo bash $0 51434.koara.live"
require_root "$@"

say "Пакеты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q python3-venv sqlite3 curl gnupg
if ! command -v caddy >/dev/null; then
    curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
        | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
        > /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -q
    apt-get install -y -q caddy
fi

say "Пользователь и папки"
id "$USER_NAME" >/dev/null 2>&1 \
    || useradd --system --home-dir "$DATA_DIR" --no-create-home --shell /usr/sbin/nologin "$USER_NAME"
install -d -o "$USER_NAME" -g "$USER_NAME" -m 750 "$DATA_DIR"
install -d -m 700 "$BACKUPS_DIR"
install -d -m 755 "$APP_DIR"
install -d -o root -g "$USER_NAME" -m 750 "$ETC_DIR"

say "Код и зависимости"
rm -rf "$APP_DIR/app.new"
cp -r "$REPO_DIR/server/app" "$APP_DIR/app.new"
find "$APP_DIR/app.new" -name '__pycache__' -prune -exec rm -rf {} +
rm -rf "$APP_DIR/app"
mv "$APP_DIR/app.new" "$APP_DIR/app"
install -m 644 "$REPO_DIR/server/requirements.txt" "$APP_DIR/requirements.txt"
[[ -x $APP_DIR/venv/bin/python ]] || python3 -m venv "$APP_DIR/venv"
"$APP_DIR/venv/bin/pip" install -q --upgrade pip
"$APP_DIR/venv/bin/pip" install -q -r "$APP_DIR/requirements.txt"

say "Ключ семьи"
NEW_KEY=
if [[ ! -f $ENV_FILE ]]; then
    NEW_KEY=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')
    printf 'FH_FAMILY_KEY=%s\nFH_DATA_DIR=%s\n' "$NEW_KEY" "$DATA_DIR" > "$ENV_FILE"
    echo "создан новый ключ"
else
    echo "ключ уже есть, не меняется"
fi
chown root:"$USER_NAME" "$ENV_FILE"
chmod 640 "$ENV_FILE"

say "Настройки отчётов"
if [[ ! -f $REPORTS_CONFIG ]]; then
    install -o root -g "$USER_NAME" -m 640 "$REPO_DIR/server/deploy/reports.json" "$REPORTS_CONFIG"
    echo "создан $REPORTS_CONFIG (время завтрака, обеда, ужина) — можно править, действует сразу"
else
    echo "$REPORTS_CONFIG уже есть, не меняется"
fi

say "Сервис systemd"
sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@DATA_DIR@|$DATA_DIR|g" -e "s|@ENV_FILE@|$ENV_FILE|g" \
    -e "s|@PORT@|$PORT|g" -e "s|@USER@|$USER_NAME|g" -e "s|@BACKUPS_DIR@|$BACKUPS_DIR|g" \
    "$REPO_DIR/server/deploy/foodhealth.service" > /etc/systemd/system/foodhealth.service
sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@ENV_FILE@|$ENV_FILE|g" -e "s|@BACKUPS_DIR@|$BACKUPS_DIR|g" \
    "$REPO_DIR/server/deploy/foodhealth-backup.service" > /etc/systemd/system/foodhealth-backup.service
install -m 644 "$REPO_DIR/server/deploy/foodhealth-backup.timer" /etc/systemd/system/foodhealth-backup.timer
sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@ENV_FILE@|$ENV_FILE|g" -e "s|@BACKUPS_DIR@|$BACKUPS_DIR|g" \
    "$REPO_DIR/server/deploy/foodhealth-backup-telegram.service" > /etc/systemd/system/foodhealth-backup-telegram.service
sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@DATA_DIR@|$DATA_DIR|g" -e "s|@ENV_FILE@|$ENV_FILE|g" -e "s|@USER@|$USER_NAME|g" \
    "$REPO_DIR/server/deploy/foodhealth-reports.service" > /etc/systemd/system/foodhealth-reports.service
install -m 644 "$REPO_DIR/server/deploy/foodhealth-reports.timer" /etc/systemd/system/foodhealth-reports.timer

say "Анализ Claude"
ANALYSIS_HOME=$(getent passwd "$ANALYSIS_USER" | cut -d: -f6 || true)
CLAUDE_BIN=
if [[ -n $ANALYSIS_HOME && -x $ANALYSIS_HOME/.local/bin/claude ]]; then
    CLAUDE_BIN=$ANALYSIS_HOME/.local/bin/claude
fi
if [[ -n $CLAUDE_BIN ]]; then
    # Пользователь с Claude Code видит папку обмена через группу сервиса
    usermod -aG "$USER_NAME" "$ANALYSIS_USER"
    for d in "$ANALYSIS_DIR" "$ANALYSIS_DIR/prompts" "$ANALYSIS_DIR/in" "$ANALYSIS_DIR/out" "$ANALYSIS_DIR/work"; do
        install -d -o "$USER_NAME" -g "$USER_NAME" -m 2770 "$d"
    done
    # Образцы промтов — только недостающие: правки не затираются
    for f in "$REPO_DIR"/server/deploy/prompts/*.md; do
        target=$ANALYSIS_DIR/prompts/$(basename "$f")
        if [[ ! -e $target ]]; then
            install -o "$USER_NAME" -g "$USER_NAME" -m 660 "$f" "$target"
            echo "промт $(basename "$f") — образец"
        fi
    done
    sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@DATA_DIR@|$DATA_DIR|g" -e "s|@ENV_FILE@|$ENV_FILE|g" -e "s|@USER@|$USER_NAME|g" \
        "$REPO_DIR/server/deploy/foodhealth-analysis-export.service" > /etc/systemd/system/foodhealth-analysis-export.service
    sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@DATA_DIR@|$DATA_DIR|g" -e "s|@USER@|$USER_NAME|g" \
        -e "s|@ANALYSIS_USER@|$ANALYSIS_USER|g" -e "s|@CLAUDE_BIN@|$CLAUDE_BIN|g" \
        "$REPO_DIR/server/deploy/foodhealth-analysis.service" > /etc/systemd/system/foodhealth-analysis.service
    install -m 644 "$REPO_DIR/server/deploy/foodhealth-analysis.timer" /etc/systemd/system/foodhealth-analysis.timer
    echo "Claude Code: $CLAUDE_BIN; промты: $ANALYSIS_DIR/prompts"
else
    echo "Claude Code у пользователя $ANALYSIS_USER не найден — ночной анализ не включён"
fi

systemctl daemon-reload
systemctl enable "$SERVICE" >/dev/null
systemctl restart "$SERVICE"
systemctl enable --now foodhealth-backup.timer >/dev/null
systemctl enable --now foodhealth-reports.timer >/dev/null
if [[ -n $CLAUDE_BIN ]]; then systemctl enable --now foodhealth-analysis.timer >/dev/null; fi
wait_local_health
echo "сервер отвечает на 127.0.0.1:$PORT"

say "Caddy (HTTPS)"
CADDYFILE=/etc/caddy/Caddyfile
if [[ -f $CADDYFILE ]] && ! grep -q '^# foodhealth' "$CADDYFILE" && ! grep -q '^:80' "$CADDYFILE"; then
    cp "$CADDYFILE" "$CADDYFILE.bak-$(date +%Y%m%d-%H%M%S)"
    echo "прежний Caddyfile сохранён рядом (.bak-…)"
fi
sed -e "s|@DOMAIN@|$DOMAIN|g" -e "s|@PORT@|$PORT|g" "$REPO_DIR/server/deploy/Caddyfile.template" > "$CADDYFILE"
caddy validate --config "$CADDYFILE" --adapter caddyfile >/dev/null
systemctl enable caddy >/dev/null
systemctl restart caddy

say "Файрвол"
if command -v ufw >/dev/null && ufw status | grep -q 'Status: active'; then
    ufw allow 80/tcp >/dev/null
    ufw allow 443/tcp >/dev/null
    echo "открыты 80/tcp и 443/tcp"
else
    echo "ufw не включён — пропускаю"
fi

say "Проверка HTTPS (сертификат может выпускаться до минуты)"
code=000
for _ in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/health" || true)
    [[ $code == 401 ]] && break
    sleep 2
done
if [[ $code == 401 ]]; then
    echo "https://$DOMAIN работает: без ключа 401, как и должно быть"
else
    echo "HTTPS пока не отвечает (код $code). Журнал Caddy: journalctl -u caddy -n 50"
fi

say "Готово"
echo "Адрес сервера:  https://$DOMAIN"
if [[ -n $NEW_KEY ]]; then
    echo "Ключ семьи:     $NEW_KEY"
    echo "Сохраните его: он вводится в настройках каждого телефона. Посмотреть позже: sudo cat $ENV_FILE"
else
    echo "Ключ семьи прежний: sudo cat $ENV_FILE"
fi
echo "Резервные копии: $BACKUPS_DIR, каждый день в 03:30"
if [[ -n $CLAUDE_BIN ]]; then
    echo "Анализ Claude: каждый день в 4:00 по Москве; проверить сейчас: sudo bash server/deploy/analysis-now.sh"
fi
if [[ -f $TELEGRAM_CONFIG ]]; then
    echo "Отчёты в Telegram: каждый день в 6:00 по Москве"
else
    echo "Отчёты в Telegram не настроены: sudo bash server/deploy/telegram-setup.sh"
fi
