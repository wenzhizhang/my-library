# =============================================================================
# Nginx 独立反向代理镜像（支持 Certbot HTTPS）
# 纯反向代理 + 自动 Let's Encrypt 证书管理
# =============================================================================
FROM nginx:alpine

# 安装 certbot (Let's Encrypt ACME 客户端) + 工具
RUN apk add --no-cache certbot curl

# 删除 nginx:alpine 自带的欢迎页配置
RUN rm /etc/nginx/conf.d/default.conf

# conf.d 目录在运行时由 start.sh 根据 DOMAIN 生成配置
# 启动前保持空目录

# 复制主配置
COPY nginx.conf /etc/nginx/nginx.conf

# 复制通用配置片段
COPY includes/ /etc/nginx/includes/

# 复制启动脚本
COPY start.sh /start.sh
RUN chmod +x /start.sh

# certbot webroot 验证目录（运行时创建，此处确保目录存在）
RUN mkdir -p /var/www/certbot/.well-known/acme-challenge

EXPOSE 80 443

ENTRYPOINT ["/start.sh"]
