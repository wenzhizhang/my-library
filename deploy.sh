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
  --tag TAG    Set image tag to deploy (default: latest)
  --no-pull    Skip pulling new images
  -h, --help   Show this help

Examples:
  $0                          # Deploy latest
  $0 --tag v1.2.0             # Deploy specific version
  TAG=latest $0               # Deploy latest via env var

Environment:
  TAG                  Image tag to deploy (default: latest)
  CCR_NAMESPACE        Tencent CCR namespace (default: my-library)
EOF
    exit 0
}

NO_PULL=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag)
            TAG="$2"; shift 2 ;;
        --no-pull)
            NO_PULL=true; shift ;;
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
    exit 1
fi

print_info "Deploying with TAG=${TAG}"

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

print_info "Cleaning up old images..."
docker image prune -f 2>/dev/null || true

print_info "Deployment complete!"
print_info "Frontend: http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost')"
print_info "Backend:  http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo 'localhost'):8000"
echo ""
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps
