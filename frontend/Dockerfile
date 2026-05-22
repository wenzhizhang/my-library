# =============================================================================
# Frontend 镜像 — 纯 React 静态文件服务器
# 不再包含 nginx，由独立的 nginx 反向代理统一入口。
# 使用 nginx:alpine 仅作为静态文件服务器。
# =============================================================================
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/build /usr/share/nginx/html

# 最小化 nginx 配置 — 仅做静态文件服务器
RUN { \
    echo 'server {'; \
    echo '    listen 3000;'; \
    echo '    server_name _;'; \
    echo '    root /usr/share/nginx/html;'; \
    echo '    index index.html;'; \
    echo '    location / {'; \
    echo '        try_files $uri $uri/ /index.html;'; \
    echo '    }'; \
    echo '    location /static/ {'; \
    echo '        expires 1y;'; \
    echo '        add_header Cache-Control "public, immutable";'; \
    echo '    }'; \
    echo '}'; \
} > /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
