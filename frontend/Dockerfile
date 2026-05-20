# Frontend image for React production build
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

# 安装 certbot (Let's Encrypt ACME 客户端)
RUN apk add --no-cache certbot

# 复制构建产物
COPY --from=builder /app/build /usr/share/nginx/html

# 复制 Nginx 配置
#   nginx.conf         → HTTPS 模板 (含 ${DOMAIN} 占位符, 启动脚本替换)
#   nginx-http-only.conf → HTTP 回退配置 (无证书时使用)
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
COPY nginx-http-only.conf /etc/nginx/conf.d/default-http-only.conf
COPY nginx-certbot.conf /etc/nginx/conf.d/nginx-certbot.conf

# 复制启动脚本
COPY start-nginx.sh /start-nginx.sh
RUN chmod +x /start-nginx.sh

# certbot webroot 验证目录
RUN mkdir -p /var/www/certbot/.well-known/acme-challenge

EXPOSE 80 443

ENTRYPOINT ["/start-nginx.sh"]
