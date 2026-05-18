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
CCR_NAMESPACE="my-library" # Default namespace for Tencent CCR, can be overridden by TENCENT_ACCOUNT

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
  TAG               Image tag to use when pushing (default: latest)
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

# TAG is used only when pushing to Tencent CCR
TAG=${DEFAULT_TAG}

# Tencent account is read from the shell environment variable
# TENCENT_ACCOUNT must be exported in your shell before running push mode.
# Example:
# export TENCENT_ACCOUNT=your_account
EOF2
    print_info "Created .env file with default SERVER_IP=${DEFAULT_IP} and TAG=${DEFAULT_TAG}"
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
        docker login --username "${TENCENT_ACCOUNT}" "${REGISTRY}"
        docker compose "${compose_args[@]}" build
        docker compose "${compose_args[@]}" push
    else
        docker compose "${compose_args[@]}" up --build
    fi
elif command -v "docker-compose" &> /dev/null; then
    print_info "Using docker-compose (legacy syntax)"
    if [ "${MODE}" = "push" ]; then
        print_info "Logging in to Tencent CCR (${REGISTRY})"
        docker login --username "${TENCENT_ACCOUNT}" "${REGISTRY}"
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
else
    print_info "Build completed!"
    print_info "Frontend: http://localhost"
    print_info "Backend API: http://localhost:8000/api/"
fi
