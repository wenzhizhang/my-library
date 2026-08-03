#!/bin/bash

# Docker Build Script for My Library
# Builds Docker images and optionally pushes them to Tencent CCR.
# With change detection: only builds/pushes images for services that changed.

set -e

# Default values
DEFAULT_IP="localhost"
REGISTRY="ccr.ccs.tencentyun.com"
MODE="up"
CCR_NAMESPACE="my-library"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---------------------------------------------------------------------------
# Change detection: map changed files to affected services
# ---------------------------------------------------------------------------
BUILD_MARKER=".build-marker"

# File patterns → affected services (space-separated list)
# Order: check specific paths first, then general patterns.
detect_changed_services() {
    local base_commit="$1"
    local changed_files
    local diff_rc=0
    changed_files=$(git diff --name-only "${base_commit}..HEAD" 2>/dev/null) || diff_rc=$?

    if [ "${diff_rc}" -ne 0 ]; then
        # git diff failed (shallow clone, corrupted index, etc.) → signal caller to build all
        echo "__GIT_ERROR__"
        return
    fi

    if [ -z "${changed_files}" ]; then
        echo ""
        return
    fi

    local svc_nginx=0
    local svc_backend=0
    local svc_frontend=0

    while IFS= read -r file; do
        [ -z "${file}" ] && continue
        case "${file}" in
            # ---- Per-service directories ----
            nginx/*)
                svc_nginx=1 ;;
            backend/*)
                svc_backend=1 ;;
            frontend/*)
                svc_frontend=1 ;;

            # ---- Config copied into backend at build time ----
            config/*)
                svc_backend=1 ;;

            # ---- Infrastructure: affects all services ----
            docker-compose.yml|docker-compose.prod.yml|build.sh|deploy.sh)
                svc_nginx=1; svc_backend=1; svc_frontend=1 ;;

            # ---- Root data scripts → backend ----
            sync_book_collections.py|sync-projects.sh|scripts/sync-projects.py)
                svc_backend=1 ;;

            # ---- Docs / runtime config only → no rebuild ----
            README.md|RAG_STRATEGY.md|RAG_DESIGN.md|DOCKER_DEPLOYMENT.md|DESIGN.md|GUI_PROMPT.md|.env|.env.example)
                ;;
            VERSION|.version-*|.build-marker|.gitignore)
                ;;

            # ---- Root-level unknowns → conservative: rebuild all ----
            *)
                svc_nginx=1; svc_backend=1; svc_frontend=1
                print_warning "Unrecognized changed file '${file}' → rebuilding all services"
                ;;
        esac
    done <<< "${changed_files}"

    local result=""
    [ "${svc_nginx}"   -eq 1 ] && result="${result} nginx"
    [ "${svc_backend}" -eq 1 ] && result="${result} backend"
    [ "${svc_frontend}" -eq 1 ] && result="${result} frontend"
    echo "${result}" | sed 's/^ *//'
}

# Read per-service version
read_svc_version() {
    local svc="$1"
    local vf=".version-${svc}"
    if [ -f "${vf}" ]; then
        head -n1 "${vf}" | tr -d '[:space:]'
    else
        # Fall back to main VERSION file
        head -n1 VERSION | tr -d '[:space:]'
    fi
}

bump_svc_version() {
    local svc="$1"
    local vf=".version-${svc}"
    local cur
    cur=$(read_svc_version "${svc}")

    # Validate version format (X.Y.Z)
    if ! echo "${cur}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
        print_error "Invalid version format in ${vf}: '${cur}'. Expected X.Y.Z"
        exit 1
    fi

    IFS='.' read -r MAJOR MINOR PATCH <<< "${cur}"
    local new_patch=$((PATCH + 1))
    local new_ver="${MAJOR}.${MINOR}.${new_patch}"
    echo "${new_ver}" > "${vf}"
    echo "${new_ver}"
}

# ---------------------------------------------------------------------------
# Usage
# ---------------------------------------------------------------------------
usage() {
    cat <<EOF
Usage: $0 [MODE] [OPTIONS]

Modes:
  up          Build and run locally (default)
  push        Build and push images to Tencent CCR (change-detection aware)
  help        Show this help message

Options:
  --no-cache  Disable Docker build cache (force full rebuild of changed images)
  --all       Force rebuild all services (skip change detection)

Environment variables:
  SERVER_IP         Set public server IP for build/runtime config
  TENCENT_ACCOUNT   Tencent cloud account name used for CCR login and image namespace
  TENCENT_PASSWORD  Tencent cloud password (optional; reads from stdin if unset)

Change detection (push mode):
  Compares HEAD against the last successful push commit (stored in .build-marker).
  Only services with changes are rebuilt and pushed. Use --all to override.
EOF
    exit 1
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
NO_CACHE=""
FORCE_ALL=false
POSITIONAL_ARGS=()
for arg in "$@"; do
    case "${arg}" in
        --no-cache) NO_CACHE="--no-cache" ;;
        --all)      FORCE_ALL=true ;;
        help|--help|-h) usage ;;
        *)          POSITIONAL_ARGS+=("${arg}") ;;
    esac
done

if [ "${#POSITIONAL_ARGS[@]}" -gt 1 ]; then
    usage
fi

if [ "${#POSITIONAL_ARGS[@]}" -eq 1 ]; then
    case "${POSITIONAL_ARGS[0]}" in
        up|push) MODE="${POSITIONAL_ARGS[0]}" ;;
        *)       usage ;;
    esac
fi

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
if [ ! -f ".env" ]; then
    print_error ".env file not found. Please copy .env.example to .env and configure your settings."
    exit 1
fi

if [ ! -f "VERSION" ]; then
    echo "0.0.1" > VERSION
    print_info "Created VERSION file with 0.0.1"
fi

# Load environment variables from .env
export $(grep -v '^#' .env | xargs)

# Check SERVER_IP
if [ -z "${SERVER_IP}" ] || [ "${SERVER_IP}" = "your-server-public-ip" ]; then
    print_warning "SERVER_IP is not set or still has placeholder value. Using localhost."
    export SERVER_IP="${DEFAULT_IP}"
fi

# ---------------------------------------------------------------------------
# Determine changed services (push mode only)
# ---------------------------------------------------------------------------
ALL_SERVICES="nginx backend frontend"
CHANGED_SERVICES=""

if [ "${MODE}" = "push" ]; then
    if [ "${FORCE_ALL}" = true ]; then
        CHANGED_SERVICES="${ALL_SERVICES}"
        print_info "--all: forcing rebuild of all services"
    else
        BASE_COMMIT=""
        if [ -f "${BUILD_MARKER}" ]; then
            BASE_COMMIT=$(head -n1 "${BUILD_MARKER}" | tr -d '[:space:]')
        fi

        if [ -z "${BASE_COMMIT}" ]; then
            print_info "No previous build marker found → building all services"
            CHANGED_SERVICES="${ALL_SERVICES}"
        elif ! git cat-file -e "${BASE_COMMIT}" 2>/dev/null; then
            print_warning "Build marker commit ${BASE_COMMIT} not in history → building all services"
            CHANGED_SERVICES="${ALL_SERVICES}"
        else
            CHANGED_SERVICES=$(detect_changed_services "${BASE_COMMIT}")
            if [ "${CHANGED_SERVICES}" = "__GIT_ERROR__" ]; then
                print_warning "git diff failed — falling back to build all services"
                CHANGED_SERVICES="${ALL_SERVICES}"
            elif [ -z "${CHANGED_SERVICES}" ]; then
                print_info "No changes detected. Nothing to build."
                exit 0
            fi
        fi
    fi

    # Check Tencent credentials
    if [ -z "${TENCENT_ACCOUNT}" ]; then
        print_error "TENCENT_ACCOUNT is required for push mode. Please export it in your shell."
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Set image tags and bump versions
# ---------------------------------------------------------------------------
declare -A SVC_VERSION
declare -A SVC_IMAGE

for svc in ${ALL_SERVICES}; do
    if [ "${MODE}" = "push" ]; then
        if echo " ${CHANGED_SERVICES} " | grep -q " ${svc} "; then
            # Changed → bump version
            NEW_VER=$(bump_svc_version "${svc}")
            SVC_VERSION[${svc}]="${NEW_VER}"
            print_info "${svc}: version bumped → ${NEW_VER}"
        else
            # Unchanged → keep current version
            SVC_VERSION[${svc}]=$(read_svc_version "${svc}")
            print_info "${svc}: unchanged (v${SVC_VERSION[${svc}]})"
        fi
        SVC_IMAGE[${svc}]="${REGISTRY}/${CCR_NAMESPACE}/my-library-${svc}:v${SVC_VERSION[${svc}]}"
    else
        # Local mode: use current version (no bump)
        SVC_VERSION[${svc}]=$(read_svc_version "${svc}")
        SVC_IMAGE[${svc}]="my-library-${svc}:local"
    fi
done

export NGINX_IMAGE="${SVC_IMAGE[nginx]}"
export BACKEND_IMAGE="${SVC_IMAGE[backend]}"
export FRONTEND_IMAGE="${SVC_IMAGE[frontend]}"

# ---------------------------------------------------------------------------
# Print summary
# ---------------------------------------------------------------------------
print_info "Mode: ${MODE}"
if [ "${MODE}" = "push" ]; then
    print_info "Nginx image:    ${NGINX_IMAGE}"
    print_info "Backend image:  ${BACKEND_IMAGE}"
    print_info "Frontend image: ${FRONTEND_IMAGE}"
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
# Copy project config into backend build context
mkdir -p backend/config
cp -f config/projects.json backend/config/projects.json

# Disable BuildKit provenance attestation
export BUILDX_NO_DEFAULT_ATTESTATIONS=1

# Detect compose command
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    print_error "Neither 'docker compose' nor 'docker-compose' found. Please install Docker."
    exit 1
fi
print_info "Using ${DOCKER_COMPOSE}"

if [ "${MODE}" = "push" ]; then
    # ---- Login ----
    print_info "Logging in to Tencent CCR (${REGISTRY})"
    if [ -n "${TENCENT_PASSWORD}" ]; then
        echo "${TENCENT_PASSWORD}" | docker login --username "${TENCENT_ACCOUNT}" --password-stdin "${REGISTRY}"
    else
        docker login --username "${TENCENT_ACCOUNT}" "${REGISTRY}"
    fi

    # ---- Build changed services ----
    print_info "Building changed services:${CHANGED_SERVICES}"
    # shellcheck disable=SC2086
    ${DOCKER_COMPOSE} --env-file .env build ${NO_CACHE} ${CHANGED_SERVICES}

    # ---- Push changed services ----
    print_info "Pushing changed services..."
    # shellcheck disable=SC2086
    ${DOCKER_COMPOSE} --env-file .env push ${CHANGED_SERVICES}

    # ---- Tag and push :latest for changed services ----
    print_info "Tagging and pushing :latest ..."
    for svc in ${CHANGED_SERVICES}; do
        VERSIONED_IMG="${SVC_IMAGE[${svc}]}"
        LATEST_IMG="${REGISTRY}/${CCR_NAMESPACE}/my-library-${svc}:latest"
        docker tag "${VERSIONED_IMG}" "${LATEST_IMG}"
        docker push "${LATEST_IMG}"
        print_info "  ${LATEST_IMG}"
    done

    # ---- Persist build marker ----
    git rev-parse HEAD > "${BUILD_MARKER}"
    print_info "Build marker updated to $(cat ${BUILD_MARKER})"

    print_info "Push completed successfully"
    for svc in ${CHANGED_SERVICES}; do
        print_info "  ${svc}: ${SVC_IMAGE[${svc}]} (latest)"
    done

else
    # ---- Local mode: build and run all ----
    print_info "Building Docker images..."
    ${DOCKER_COMPOSE} --env-file .env up --build
fi
