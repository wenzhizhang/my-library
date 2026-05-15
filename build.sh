#!/bin/bash

# Docker Build Script for My Library
# This script helps build Docker images with dynamic server IP configuration

set -e

# Default values
DEFAULT_IP="localhost"
DOCKER_COMPOSE_FILE="docker-compose.yml"

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

# Check if .env file exists
if [ ! -f ".env" ]; then
    print_warning ".env file not found. Creating template..."
    cat > .env << EOF
# Docker Compose Environment Variables
# Set your server public IP address here
SERVER_IP=${DEFAULT_IP}

# Example:
# SERVER_IP=111.229.109.204
# SERVER_IP=dingfengbo.top
EOF
    print_info "Created .env file with default SERVER_IP=${DEFAULT_IP}"
    print_warning "Please edit .env file and set your SERVER_IP before deployment!"
fi

# Load environment variables
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

print_info "Building with SERVER_IP=${SERVER_IP}"

# Build and run
print_info "Building Docker images..."

# Try docker compose first (newer versions)
if command -v "docker" &> /dev/null && docker compose version &> /dev/null; then
    print_info "Using docker compose (new syntax)"
    docker compose --env-file .env up --build
elif command -v "docker-compose" &> /dev/null; then
    print_info "Using docker-compose (legacy syntax)"
    docker-compose --env-file .env up --build
else
    print_error "Neither 'docker compose' nor 'docker-compose' found. Please install Docker."
    exit 1
fi

print_info "Build completed!"
print_info "Frontend: http://localhost"
print_info "Backend API: http://localhost:8000/api/"