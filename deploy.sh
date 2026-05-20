#!/bin/bash

# Deploy Script for My Library (Production Server)
# Run this on the production server to pull latest images and restart containers.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.prod.yml"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

print_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --tag TAG       Set image tag to deploy (default: latest)
  --no-pull       Skip pulling new images
  --init          (Re)generate .env template and exit
  --seed-db FILE  Copy local SQLite database into the persistent volume
  -h, --help      Show this help

First-time setup:
  $0 --init                        # Create .env template, then edit it
  $0                               # Deploy with settings in .env
  $0 --seed-db ./demo.db           # Deploy and import local database

Examples:
  $0                               # Deploy latest
  $0 --tag v1.2.0                  # Deploy specific version
  $0 --seed-db /path/to/demo.db    # Import database on deploy
  TAG=latest $0                    # Deploy latest via env var

Environment:
  TAG                  Image tag to deploy (default: latest)
  CCR_NAMESPACE        Tencent CCR namespace (default: my-library)
EOF
    exit 0
}

NO_PULL=false
INIT_ONLY=false
SEED_DB_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag)
            TAG="$2"; shift 2 ;;
        --no-pull)
            NO_PULL=true; shift ;;
        --init)
            INIT_ONLY=true; shift ;;
        --seed-db)
            SEED_DB_FILE="$2"; shift 2 ;;
        -h|--help)
            usage ;;
        *)
            print_error "Unknown option: $1"
            usage ;;
    esac
done

TAG="${TAG:-latest}"
export TAG

if [ ! -f "${COMPOSE_FILE}" ]; then
    print_error "Compose file not found: ${COMPOSE_FILE}"
    print_info "Please place docker-compose.prod.yml in the same directory as this script."
    print_info "You can download it from the project repository."
    exit 1
fi

# =============================================================================
# .env 自举: 如果 .env 不存在, 生成模板并引导用户配置
# =============================================================================
ENV_FILE="${SCRIPT_DIR}/.env"

init_env() {
    if [ -f "${ENV_FILE}" ]; then
        print_warn ".env already exists, overwriting..."
    fi
    cat > "${ENV_FILE}" << 'EOF'
# =============================================================================
# My Library — 生产环境配置
# =============================================================================

# (可选) 服务器公网 IP, 仅在无域名时使用
SERVER_IP=

# =============================================================================
# Let's Encrypt / HTTPS 配置
# =============================================================================
# 你的域名, 用于 TLS 证书签发 (例如: my-library.example.com)
# 留空则仅在 HTTP 下运行
DOMAIN=

# certbot 注册邮箱, 用于 Let's Encrypt 证书过期提醒
CERTBOT_EMAIL=

# =============================================================================
# 镜像标签 (默认 latest, 也可通过 --tag 参数覆盖)
# =============================================================================
TAG=latest

# =============================================================================
# 腾讯云 CCR 命名空间 (默认 my-library)
# =============================================================================
CCR_NAMESPACE=my-library
EOF
    print_info ".env template created at ${ENV_FILE}"
    print_info "Please edit .env and set DOMAIN / CERTBOT_EMAIL for HTTPS, then re-run deploy.sh"
}

if [ ! -f "${ENV_FILE}" ]; then
    print_warn ".env not found, generating template..."
    init_env
    if [ "${INIT_ONLY}" = false ]; then
        print_info "Edit .env and re-run deploy.sh to deploy."
        print_info "Or run: $0 --init    to just (re)generate the .env template."
        exit 0
    fi
fi

if [ "${INIT_ONLY}" = true ]; then
    init_env
    exit 0
fi

# Load .env
export $(grep -v '^#' "${ENV_FILE}" | xargs)

# TAG 允许再次被命令行 --tag 覆盖
TAG="${TAG:-latest}"
export TAG

print_info "Deploying with TAG=${TAG}"

# =============================================================================
# HTTPS 配置检查
# =============================================================================
if [ -n "${DOMAIN}" ]; then
    if [ -z "${CERTBOT_EMAIL}" ]; then
        print_warn "DOMAIN is set but CERTBOT_EMAIL is empty."
        print_warn "certbot may fail to register — set CERTBOT_EMAIL in .env"
    fi
    print_info "HTTPS will be enabled for: ${DOMAIN}"
else
    print_warn "DOMAIN not set — running in HTTP-only mode."
    print_warn "To enable HTTPS, set DOMAIN and CERTBOT_EMAIL in .env"
fi

# Support both docker compose (v2) and docker-compose (v1)
if command -v docker &> /dev/null && docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    print_error "Docker Compose not found"
    exit 1
fi

if [ "${NO_PULL}" = false ]; then
    print_info "Pulling latest images..."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" pull
fi

print_info "Recreating containers..."
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up -d --remove-orphans

# =============================================================================
# 数据库迁移 (--seed-db)
# =============================================================================
if [ -n "${SEED_DB_FILE}" ]; then
    if [ ! -f "${SEED_DB_FILE}" ]; then
        print_error "Seed database file not found: ${SEED_DB_FILE}"
        exit 1
    fi

    print_info "Importing database: ${SEED_DB_FILE}"

    # 等待 backend 容器启动
    BACKEND_NAME="$(${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps -q backend 2>/dev/null)"
    if [ -z "${BACKEND_NAME}" ]; then
        print_error "Backend container not found after deploy"
        exit 1
    fi

    print_info "Stopping backend to import database..."
    docker stop "${BACKEND_NAME}" >/dev/null

    print_info "Copying ${SEED_DB_FILE} → /app/data/demo.db ..."
    docker cp "${SEED_DB_FILE}" "${BACKEND_NAME}:/app/data/demo.db"

    print_info "Starting backend..."
    docker start "${BACKEND_NAME}" >/dev/null

    print_info "Database imported"
fi

print_info "Cleaning up old images..."
docker image prune -f 2>/dev/null || true

print_info "Deployment complete!"
if [ -n "${DOMAIN}" ]; then
    print_info "Frontend: https://${DOMAIN}"
    print_info "Backend:  https://${DOMAIN}/api/"
    echo ""
    print_info "Certificate status:"
    print_info "   To check cert logs: docker logs \$(docker ps -qf name=frontend) 2>&1 | grep certbot"
    print_info "   Manual renewal:     docker exec \$(docker ps -qf name=frontend) certbot renew --webroot -w /var/www/certbot"
    print_info "   Cron auto-renewal:  runs daily at 02:00 and 14:00 inside the container"
else
    print_info "Frontend: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost')"
    print_info "Backend:  http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost'):8000"
    echo ""
    print_warn "HTTPS not configured. Set DOMAIN and CERTBOT_EMAIL in .env for TLS."
fi
echo ""
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps
