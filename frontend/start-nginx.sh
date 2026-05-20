#!/bin/sh
# =============================================================================
# Nginx + Let's Encrypt (certbot) 启动脚本
#
# 流程:
#   1. 未设置 DOMAIN → HTTP-only 模式直接启动
#   2. 已设置 DOMAIN 但无证书 → 临时 HTTP 启动, certbot 获取证书, 切到 HTTPS
#   3. 已设置 DOMAIN 且有证书 → certbot renew 检查, 直接 HTTPS 启动
#
# 续期: crond 每天凌晨 2:00 和 14:00 检查证书续期
# =============================================================================
set -e

DOMAIN="${DOMAIN:-}"
EMAIL="${CERTBOT_EMAIL:-}"

HTTP_CONF="/etc/nginx/conf.d/default-http-only.conf"
CERTBOT_CONF="/etc/nginx/conf.d/nginx-certbot.conf"
HTTPS_TEMPLATE="/etc/nginx/conf.d/default.conf.template"
TARGET_CONF="/etc/nginx/conf.d/default.conf"

CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
CERT_FILE="${CERT_DIR}/fullchain.pem"
CERTBOT_WWW="/var/www/certbot"

# ---------------------------------------------------------------------------
# 每月续期钩子脚本 (certbot 调用后重载 nginx)
# ---------------------------------------------------------------------------
RENEW_HOOK="/etc/letsencrypt/renewal-hooks/deploy/nginx-reload.sh"
mkdir -p "$(dirname "$RENEW_HOOK")"
cat > "$RENEW_HOOK" << 'NGINX_RELOAD'
#!/bin/sh
echo "[certbot] Reloading nginx after renewal..."
nginx -s reload
NGINX_RELOAD
chmod +x "$RENEW_HOOK"

# ---------------------------------------------------------------------------
# 等待 backend DNS 可解析 (Nginx 启动时会解析 upstream 主机名)
# ---------------------------------------------------------------------------
wait_for_backend() {
    echo "[start-nginx] Waiting for backend DNS..."
    for i in $(seq 1 30); do
        if getent hosts backend >/dev/null 2>&1; then
            echo "[start-nginx] Backend DNS resolved after $((i*2))s"
            return 0
        fi
        sleep 2
    done
    echo "[start-nginx] WARNING: Backend DNS not resolved — Nginx may fail to start"
    return 1
}

# ===========================================================================
# 1. 无域名 → HTTP-only 模式
# ===========================================================================
if [ -z "$DOMAIN" ]; then
    echo "============================================"
    echo "[start-nginx] DOMAIN not set"
    echo "[start-nginx] Starting Nginx in HTTP-only mode"
    echo "============================================"
    cp "$HTTP_CONF" "$TARGET_CONF"
    wait_for_backend
    exec nginx -g "daemon off;"
fi

echo "============================================"
echo "[start-nginx] Domain: ${DOMAIN}"
echo "============================================"

# ===========================================================================
# 2. 证书存在 → 尝试续期
# ===========================================================================
if [ -f "$CERT_FILE" ]; then
    echo "[start-nginx] Certificate found, checking renewal..."
    certbot renew --webroot -w "$CERTBOT_WWW" --non-interactive --quiet --deploy-hook "$RENEW_HOOK" || true
    echo "[start-nginx] Renewal check complete"
else
    # =======================================================================
    # 3. 无证书 → 获取新证书
    # =======================================================================
    echo "[start-nginx] No certificate for ${DOMAIN}, obtaining..."

    # 3a. 用最小化配置临时启动 nginx (后台, 仅用于 certbot 验证)
    echo "[start-nginx] Starting temporary nginx for ACME challenge..."
    cp "$CERTBOT_CONF" "$TARGET_CONF"
    nginx -g "daemon off;" &
    NGINX_PID=$!
    sleep 2

    # 3b. 检查临时 nginx 是否存活
    if ! kill -0 $NGINX_PID 2>/dev/null; then
        echo "[start-nginx] ERROR: Temporary nginx failed to start"
        echo "[start-nginx] nginx error log:"
        cat /var/log/nginx/error.log 2>/dev/null || true
        exit 1
    fi
    echo "[start-nginx] Temporary nginx running (PID $NGINX_PID)"

    # 3c. 确保 webroot 目录存在
    mkdir -p "${CERTBOT_WWW}/.well-known/acme-challenge"

    # 3d. 运行 certbot 获取证书
    echo "[start-nginx] Running certbot..."
    if certbot certonly --webroot \
        -w "$CERTBOT_WWW" \
        -d "$DOMAIN" \
        --email "${EMAIL}" \
        --agree-tos \
        --non-interactive \
        --force-renewal \
        --deploy-hook "$RENEW_HOOK"; then
        echo "[start-nginx] Certificate obtained successfully"
    else
        echo "[start-nginx] ERROR: Failed to obtain certificate"
        kill $NGINX_PID 2>/dev/null || true
        wait $NGINX_PID 2>/dev/null || true
        exit 1
    fi

    # 3e. 停止临时 nginx
    echo "[start-nginx] Stopping temporary nginx..."
    kill $NGINX_PID 2>/dev/null || true
    wait $NGINX_PID 2>/dev/null || true
    sleep 1
fi

# ===========================================================================
# 4. 生成 HTTPS 配置 (用 DOMAIN 替换模板变量)
# ===========================================================================
echo "[start-nginx] Generating HTTPS config for ${DOMAIN}..."
sed "s/\${DOMAIN}/${DOMAIN}/g" "$HTTPS_TEMPLATE" > "$TARGET_CONF"

# ===========================================================================
# 5. 配置 cron 自动续期 (每天 2:00 和 14:00)
# ===========================================================================
CRON_FILE="/var/spool/cron/crontabs/root"
mkdir -p "$(dirname "$CRON_FILE")"
cat > "$CRON_FILE" << CRON_EOF
0 2,14 * * * certbot renew --webroot -w ${CERTBOT_WWW} --quiet --deploy-hook /etc/letsencrypt/renewal-hooks/deploy/nginx-reload.sh 2>&1 | logger -t certbot
CRON_EOF
chmod 600 "$CRON_FILE"

echo "[start-nginx] Starting crond for auto-renewal..."
crond -b -l 8

# ===========================================================================
# 6. 前台启动 Nginx (HTTPS)
# ===========================================================================
echo "============================================"
echo "[start-nginx] Starting Nginx with HTTPS..."
echo "============================================"
wait_for_backend
exec nginx -g "daemon off;"
