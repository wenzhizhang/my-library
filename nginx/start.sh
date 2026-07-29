#!/bin/sh
# =============================================================================
# Nginx + Certbot 启动入口
#
# 行为:
#   DOMAIN 未设置 → HTTP-only 模式，直接代理
#   DOMAIN 已设置 → 获取/续期证书，启动 HTTPS
# =============================================================================
set -e

DOMAIN="${DOMAIN:-}"
EMAIL="${CERTBOT_EMAIL:-}"
CERTBOT_WWW="/var/www/certbot"
CONF_DIR="/etc/nginx/conf.d"

mkdir -p "${CERTBOT_WWW}/.well-known/acme-challenge"

# ============================================================================
# 配置生成函数
# ============================================================================

# HTTP-only: DOMAIN 未设置时使用，直接代理到后端
gen_http_only() {
    cat > "${CONF_DIR}/http.conf" << 'NGX'
server {
    listen 80;
    server_name _;

    location / {
        set $frontend_upstream "http://frontend:3000";
        proxy_pass $frontend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/ {
        set $frontend_upstream "http://frontend:3000";
        proxy_pass $frontend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /api/media/ {
        set $backend_upstream "http://backend:8000";
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /api/ {
        set $backend_upstream "http://backend:8000";
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/api/ {
        set $backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ $1 break;
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/docs {
        set $backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ $1 break;
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/openapi.json {
        set $backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ $1 break;
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/redoc {
        set $backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ $1 break;
        proxy_pass $backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
}
NGX
}

# HTTP 重定向 + ACME: DOMAIN 设置后使用，80 端口只做 ACME 验证和 HTTPS 跳转
gen_http_redirect() {
    cat > "${CONF_DIR}/http.conf" << NGX
server {
    listen 80;
    server_name ${DOMAIN};

    access_log /var/log/nginx/my-library.access.log main;
    error_log  /var/log/nginx/my-library.error.log warn;

    location /.well-known/acme-challenge/ {
        root ${CERTBOT_WWW};
    }
    location / {
        return 301 https://\$host\$request_uri;
    }
}
NGX
}

# HTTPS: DOMAIN 设置后使用，443 端口承载实际应用
gen_https() {
    cat > "${CONF_DIR}/https.conf" << NGX
server {
    listen 443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;

    location /api/media/ {
        set \$backend_upstream "http://backend:8000";
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    access_log /var/log/nginx/my-library.access.log main;
    error_log  /var/log/nginx/my-library.error.log warn;

    location / {
        set \$frontend_upstream "http://frontend:3000";
        proxy_pass \$frontend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/ {
        set \$frontend_upstream "http://frontend:3000";
        proxy_pass \$frontend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /api/ {
        set \$backend_upstream "http://backend:8000";
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/api/ {
        set \$backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ \$1 break;
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /solar/ {
        set \$solar_upstream "http://solar:80";
        proxy_pass \$solar_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/docs {
        set \$backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ \$1 break;
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/openapi.json {
        set \$backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ \$1 break;
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
    location /my-library/redoc {
        set \$backend_upstream "http://backend:8000";
        rewrite ^/my-library(.*)$ \$1 break;
        proxy_pass \$backend_upstream;
        include /etc/nginx/includes/proxy-common.conf;
    }
}
NGX
}

# 临时最小化配置：仅用于 certbot ACME 验证
gen_certbot_temp() {
    cat > "${CONF_DIR}/http.conf" << 'NGX'
server {
    listen 80;
    server_name _;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / { return 444; }
}
NGX
}

# ============================================================================
# 主流程
# ============================================================================

if [ -z "$DOMAIN" ]; then
    # ── HTTP-only 模式 ──
    echo "[start-nginx] DOMAIN not set — HTTP-only mode"
    gen_http_only
else
    echo "[start-nginx] Domain: ${DOMAIN}"

    CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
    CERT_FILE="${CERT_DIR}/fullchain.pem"

    if [ ! -f "$CERT_FILE" ]; then
        # ── 首次获取证书 ──
        echo "[start-nginx] No certificate found, obtaining..."

        gen_certbot_temp
        nginx -g "daemon off;" &
        NGINX_PID=$!
        sleep 2

        if ! kill -0 $NGINX_PID 2>/dev/null; then
            echo "[start-nginx] ERROR: Temporary nginx failed to start"
            cat /var/log/nginx/error.log 2>/dev/null || true
            gen_http_only
            echo "[start-nginx] Falling back to HTTP-only mode"
        else
            # ── 运行 certbot ──
            CERTBOT_OK=false
            if [ -n "${EMAIL}" ]; then
                echo "[start-nginx] Requesting certificate with email ${EMAIL}..."
                if certbot certonly --webroot \
                    -w "${CERTBOT_WWW}" \
                    -d "${DOMAIN}" \
                    --email "${EMAIL}" \
                    --agree-tos \
                    --non-interactive \
                    --force-renewal; then
                    CERTBOT_OK=true
                fi
            else
                echo "[start-nginx] CERTBOT_EMAIL not set, obtaining certificate without email..."
                if certbot certonly --webroot \
                    -w "${CERTBOT_WWW}" \
                    -d "${DOMAIN}" \
                    --register-unsafely-without-email \
                    --agree-tos \
                    --non-interactive \
                    --force-renewal; then
                    CERTBOT_OK=true
                fi
            fi

            kill $NGINX_PID 2>/dev/null || true
            wait $NGINX_PID 2>/dev/null || true

            if [ "$CERTBOT_OK" = true ]; then
                echo "[start-nginx] Certificate obtained successfully"
                # ── 生成 HTTPS 配置 ──
                gen_http_redirect
                gen_https
            else
                echo "[start-nginx] Certificate acquisition failed, falling back to HTTP-only"
                gen_http_only

                # ── 后台自动重试证书获取（前端启动后不影响已有流量） ──
                (
                    # 初始等待：绕开 Let's Encrypt 速率限制（5次/小时）
                    RETRY_WAIT=900
                    while [ ! -f "${CERT_DIR}/fullchain.pem" ]; do
                        RETRY_WAIT=$(( RETRY_WAIT * 2 ))
                        [ $RETRY_WAIT -gt 3600 ] && RETRY_WAIT=3600
                        echo "[start-nginx] Retrying certificate in ${RETRY_WAIT}s..."
                        sleep $RETRY_WAIT

                        gen_certbot_temp
                        nginx -s reload
                        sleep 2

                        CERTBOT_RETRY_OK=false
                        if [ -n "${EMAIL}" ]; then
                            if certbot certonly --webroot \
                                -w "${CERTBOT_WWW}" \
                                -d "${DOMAIN}" \
                                --email "${EMAIL}" \
                                --agree-tos \
                                --non-interactive \
                                --force-renewal; then
                                CERTBOT_RETRY_OK=true
                            fi
                        else
                            if certbot certonly --webroot \
                                -w "${CERTBOT_WWW}" \
                                -d "${DOMAIN}" \
                                --register-unsafely-without-email \
                                --agree-tos \
                                --non-interactive \
                                --force-renewal; then
                                CERTBOT_RETRY_OK=true
                            fi
                        fi

                        if [ "$CERTBOT_RETRY_OK" = true ]; then
                            echo "[start-nginx] Certificate obtained successfully!"
                            gen_http_redirect
                            gen_https
                            nginx -s reload
                            break
                        else
                            echo "[start-nginx] Certificate still unavailable — restoring HTTP-only config"
                            gen_http_only
                            nginx -s reload
                        fi
                    done
                ) &
            fi
        fi
    else
        # ── 证书已存在 -> 续期检查 ──
        echo "[start-nginx] Certificate exists, checking renewal..."
        certbot renew --webroot -w "${CERTBOT_WWW}" \
            --non-interactive --quiet \
            --deploy-hook "nginx -s reload" || true
        gen_http_redirect
        gen_https
    fi


    # ── 后台自动续期（每天检查一次） ──
    (
        while :; do
            sleep 86400
            certbot renew --webroot -w "${CERTBOT_WWW}" \
                --quiet --deploy-hook "nginx -s reload" 2>&1 | logger -t certbot
        done
    ) &
fi

echo "[start-nginx] Starting nginx..."
exec nginx -g "daemon off;"
