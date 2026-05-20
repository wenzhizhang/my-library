#!/bin/bash

# Docker Build Script for My Library
# This script helps build Docker images and optionally push them to Tencent CCR.

set -e

# Default values
DEFAULT_IP="localhost"
DEFAULT_TAG="latest"
REGISTRY="ccr.ccs.tencentyun.com"
DOCKER_COMPOSE_FILE="docker-compose.yml"
MODE="up"
CCR_NAMESPACE="my-library"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    cat <<EOF2
Usage: $0 [MODE]

Modes:
  up          Build and run locally (default)
  push        Build and push images to Tencent CCR
  help        Show this help message

Environment variables:
  SERVER_IP         Set public server IP for build/runtime config
  TENCENT_ACCOUNT   Tencent cloud account name used for CCR login and image namespace

Version is auto-managed via the VERSION file (X.Y.Z format).
Patch version auto-increments on each push build (e.g., 0.1.0 → 0.1.1).
Image tag is derived as v{version} (e.g., v0.1.0).
EOF2
    exit 1
}

if [ "$#" -gt 1 ]; then
    usage
fi

if [ "$#" -eq 1 ]; then
    case "$1" in
        push)
            MODE="push"
            ;;
        up|run)
            MODE="up"
            ;;
        help|-h|--help)
            usage
            ;;
        *)
            print_error "Unknown mode: $1"
            usage
            ;;
    esac
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    print_warning ".env file not found. Creating template..."
    cat > .env <<EOF2
# Docker Compose Environment Variables
# Set your server public IP address here
SERVER_IP=${DEFAULT_IP}

# Version is auto-managed via VERSION file (X.Y.Z format, e.g., 0.1.0)
# Patch version auto-increments on each 'push' build
# TAG is derived from VERSION (vX.Y.Z) — do not edit manually

# Tencent account is read from the shell environment variable
# TENCENT_ACCOUNT must be exported in your shell before running push mode.
# Example:
# export TENCENT_ACCOUNT=your_account
EOF2
    print_info "Created .env file with default SERVER_IP=${DEFAULT_IP}"
    print_warning "Please edit .env file and set your SERVER_IP before deployment if needed!"
fi

# Load environment variables from .env
if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Check SERVER_IP
if [ -z "${SERVER_IP}" ] || [ "${SERVER_IP}" = "your-server-public-ip" ]; then
    print_warning "SERVER_IP not set or using placeholder value."
    print_warning "Using default: ${DEFAULT_IP}"
    print_warning "For production deployment, please set SERVER_IP in .env file"
    export SERVER_IP=${DEFAULT_IP}
fi

TAG="${TAG:-$DEFAULT_TAG}"

# Read current version from VERSION file
VERSION_FILE="VERSION"
if [ ! -f "${VERSION_FILE}" ]; then
    echo "0.1.0" > "${VERSION_FILE}"
    print_warning "VERSION file not found, created with initial version 0.1.0"
fi
CURRENT_VERSION="$(head -n1 "${VERSION_FILE}" | tr -d '[:space:]')"

# Validate version format (X.Y.Z)
if ! echo "${CURRENT_VERSION}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    print_error "Invalid version format in VERSION file: '${CURRENT_VERSION}'. Expected format: X.Y.Z (e.g., 0.1.0)"
    exit 1
fi

if [ "${MODE}" = "push" ]; then
    # Auto-increment patch version for push mode
    IFS='.' read -r MAJOR MINOR PATCH <<< "${CURRENT_VERSION}"
    NEW_PATCH=$((PATCH + 1))
    NEW_VERSION="${MAJOR}.${MINOR}.${NEW_PATCH}"
    echo "${NEW_VERSION}" > "${VERSION_FILE}"
    TAG="v${NEW_VERSION}"
    print_info "Version bumped: ${CURRENT_VERSION} → ${NEW_VERSION} (tag: ${TAG})"
else
    TAG="v${CURRENT_VERSION}"
    print_info "Using current version: ${CURRENT_VERSION} (tag: ${TAG})"
fi

if [ "${MODE}" = "push" ]; then
    if [ -z "${TENCENT_ACCOUNT}" ]; then
        print_error "TENCENT_ACCOUNT is required for push mode. Please export it in your shell."
        exit 1
    fi
    export BACKEND_IMAGE="${REGISTRY}/${CCR_NAMESPACE}/my-library-backend:${TAG}"
    export FRONTEND_IMAGE="${REGISTRY}/${CCR_NAMESPACE}/my-library-frontend:${TAG}"
else
    export BACKEND_IMAGE="my-library-backend:local"
    export FRONTEND_IMAGE="my-library-frontend:local"
fi

print_info "Mode: ${MODE}"
print_info "Building with SERVER_IP=${SERVER_IP} TAG=${TAG}"
print_info "Backend image: ${BACKEND_IMAGE}"
print_info "Frontend image: ${FRONTEND_IMAGE}"

# Build and run or push
print_info "Building Docker images..."

compose_args=(--env-file .env)

if command -v "docker" &> /dev/null && docker compose version &> /dev/null; then
    print_info "Using docker compose (new syntax)"
    if [ "${MODE}" = "push" ]; then
        print_info "Logging in to Tencent CCR (${REGISTRY})"
        if [ -n "${TENCENT_PASSWORD}" ]; then
            echo "${TENCENT_PASSWORD}" | docker login --username "${TENCENT_ACCOUNT}" --password-stdin "${REGISTRY}"
        else
            docker login --username "${TENCENT_ACCOUNT}" "${REGISTRY}"
        fi
        docker compose "${compose_args[@]}" build
        docker compose "${compose_args[@]}" push
    else
        docker compose "${compose_args[@]}" up --build
    fi
elif command -v "docker-compose" &> /dev/null; then
    print_info "Using docker-compose (legacy syntax)"
    if [ "${MODE}" = "push" ]; then
        print_info "Logging in to Tencent CCR (${REGISTRY})"
        if [ -n "${TENCENT_PASSWORD}" ]; then
            echo "${TENCENT_PASSWORD}" | docker login --username "${TENCENT_ACCOUNT}" --password-stdin "${REGISTRY}"
        else
            docker login --username "${TENCENT_ACCOUNT}" "${REGISTRY}"
        fi
        docker-compose "${compose_args[@]}" build
        docker-compose "${compose_args[@]}" push
    else
        docker-compose "${compose_args[@]}" up --build
    fi
else
    print_error "Neither 'docker compose' nor 'docker-compose' found. Please install Docker."
    exit 1
fi

if [ "${MODE}" = "push" ]; then
    print_info "Push completed successfully"
    print_info "Backend pushed: ${BACKEND_IMAGE}"
    print_info "Frontend pushed: ${FRONTEND_IMAGE}"
    # Also tag and push as latest
    print_info "Tagging and pushing latest..."
    docker tag "${BACKEND_IMAGE}" "${REGISTRY}/${CCR_NAMESPACE}/my-library-backend:latest"
    docker push "${REGISTRY}/${CCR_NAMESPACE}/my-library-backend:latest"
    docker tag "${FRONTEND_IMAGE}" "${REGISTRY}/${CCR_NAMESPACE}/my-library-frontend:latest"
    docker push "${REGISTRY}/${CCR_NAMESPACE}/my-library-frontend:latest"
    print_info "Latest tag pushed successfully"
else
    print_info "Build completed!"
    print_info "Frontend: http://localhost"
    print_info "Backend API: http://localhost:8000/api/"
fi