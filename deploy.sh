#!/bin/bash

# Deploy Script for My Library (Production Server)
# Run this on the production server to pull latest images and restart containers.
# Images use per-service versioning — each service has its own version and
# :latest tag. Deploy with :latest (default) to always get the newest of each service.

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
  --no-pull       Skip pulling new images
  --init          (Re)generate .env template and exit
  --seed-db FILE  Copy local SQLite database into the persistent volume
  -h, --help      Show this help

First-time setup:
  $0 --init                        # Create .env template, then edit it
  $0                               # Deploy with settings in .env
  $0 --seed-db ./demo.db           # Deploy and import local database

Examples:
  $0                               # Deploy latest of each service (recommended)
  $0 --no-pull                     # Restart containers without pulling
  $0 --seed-db /path/to/demo.db    # Import database on deploy

Environment:
  CCR_NAMESPACE        Tencent CCR namespace (default: my-library)

Per-service versioning:
  Each service (nginx, backend, frontend) has its own version and :latest tag.
  Deploy always pulls :latest by default — the newest image of each service.

  To roll back a specific service, edit docker-compose.prod.yml and pin the
  image tag, then redeploy. Example:
    image: ccr.ccs.tencentyun.com/my-library/my-library-backend:v0.3.17
  Then run: $0 --no-pull
EOF
    exit 0
}
NO_PULL=false
INIT_ONLY=false
SEED_DB_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
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

if [ ! -f "${COMPOSE_FILE}" ]; then
    print_error "Compose file not found: ${COMPOSE_FILE}"
    print_info "Please place docker-compose.prod.yml in the same directory as this script."
    print_info "You can download it from the project repository."
    exit 1
fi

# =============================================================================
# 配置文件检查
# backend/config 通过 bind mount 进入容器: 新文件若未同步到服务器仓库,
# 容器不会启动失败, 但对应功能会报错 —— 提前警告并给出修复方式
# =============================================================================
CONFIG_DIR="${SCRIPT_DIR}/backend/config"
for CFG in backgrounds.json purchase_stores.json; do
    if [ ! -f "${CONFIG_DIR}/${CFG}" ]; then
        print_warn "config/${CFG} not found at ${CONFIG_DIR}/${CFG}"
        print_warn "  Run 'git pull' in this repository (or copy the file), then redeploy."
        print_warn "  Background selection will use the built-in fallback until the file is present."
    fi
done

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
# Let's Encrypt / HTTPS 配置（设置后启用自动 HTTPS）
# =============================================================================
# 你的域名, 用于 Let's Encrypt 证书签发 (例如: my-library.example.com)
# 留空则仅在 HTTP 下运行
DOMAIN=

# certbot 注册邮箱, 用于 Let's Encrypt 证书过期提醒
CERTBOT_EMAIL=

# =============================================================================
# 镜像标签：三个服务各自独立升版，版本号（如 v0.3.42）只对刚变更的服务存在，
# 写成版本号会让其余服务拉不到镜像。保持 latest —— 每个服务的 latest 就是它自己的最新版。
# =============================================================================
TAG=latest

# =============================================================================
# 腾讯云 CCR 命名空间 (默认 my-library)
# =============================================================================
CCR_NAMESPACE=my-library
EOF
    print_info ".env template created at ${ENV_FILE}"
    print_info "Set DOMAIN and CERTBOT_EMAIL in .env to enable HTTPS, then re-run deploy.sh"
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

# TAG defaults to latest; can be overridden via .env TAG=...
TAG="${TAG:-latest}"
export TAG

print_info "Deploying with TAG=${TAG}"

# =============================================================================
# HTTP / HTTPS 模式
# =============================================================================
# HTTPS 检查
if [ -n "${DOMAIN}" ]; then
    print_info "HTTPS will be enabled for: ${DOMAIN}"
else
    print_info "Starting Nginx reverse proxy on port 80 (HTTP-only)"
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
    print_info "Pulling images for TAG=${TAG}..."
    # 逐服务拉取，任何一个失败就在这里停下。
    #
    # 各服务是各自升版的：build.sh 只给"检测到变更"的服务升版本号，所以 build.sh 刚打印的
    # v0.3.42 只存在于 frontend，backend / nginx / solar 都还停在各自的版本上。用这样的 TAG
    # 部署时，整批 pull 只会拉到一个，然后 up -d 把那个容器重建掉，其余拉不到 —— 代理失去上游，
    # 用户看到的是 502，日志里却只有一句拉取失败。逐个拉、并且指名是哪个服务，才不会静默半部署。
    for svc in $(${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" config --services 2>/dev/null); do
        if ! ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" pull "${svc}"; then
            print_error "无法拉取 ${svc}（TAG=${TAG}）"
            print_info "各服务独立升版，版本号 TAG 只对刚变更过的服务存在。"
            print_info "请用 TAG=latest 部署（每个服务的 latest 就是它自己的最新版），或不要设置 TAG。"
            exit 1
        fi
    done
    print_info "TAG=${TAG} 的镜像已就绪"
fi

print_info "Recreating containers..."
UP_ARGS="-d --remove-orphans"
# --no-pull 要名副其实：compose 里写着 pull_policy: always，不显式 --pull never 它照样联网拉取，
# 也就照样可能只拉到一半。这也是上面那条 502 的另一扇门。
[ "${NO_PULL}" = true ] && UP_ARGS="${UP_ARGS} --pull never"
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up ${UP_ARGS}

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
    print_info "Nginx proxy: https://${DOMAIN}"
    print_info "Backend API: https://${DOMAIN}/api/"
    echo ""
    print_info "Certificate renewal: runs daily inside container (auto)"
    print_info "Manual check: docker exec \$(docker ps -qf name=nginx) certbot renew --webroot -w /var/www/certbot --dry-run"
else
    print_info "Nginx proxy: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost')"
    print_info "Backend API: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost'):8000/api/"
fi
print_info ""
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps
