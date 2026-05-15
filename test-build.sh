#!/bin/bash

# Simple Docker Build Test Script
# This script tests basic Docker functionality without complex configurations

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_info "Testing Docker environment..."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed or not in PATH"
    exit 1
fi

print_info "Docker version: $(docker --version)"

# Test docker compose vs docker-compose
if docker compose version &> /dev/null; then
    print_info "Using 'docker compose' (new syntax)"
    DOCKER_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
    print_info "Using 'docker-compose' (legacy syntax)"
    DOCKER_CMD="docker-compose"
else
    print_error "Neither 'docker compose' nor 'docker-compose' found"
    exit 1
fi

print_info "Testing basic Docker functionality..."

# Try to build just the backend service
print_info "Building backend service..."
$DOCKER_CMD build backend

if [ $? -eq 0 ]; then
    print_info "Backend build successful!"
else
    print_error "Backend build failed"
    exit 1
fi

# Try to build just the frontend service
print_info "Building frontend service..."
$DOCKER_CMD build frontend

if [ $? -eq 0 ]; then
    print_info "Frontend build successful!"
else
    print_error "Frontend build failed"
    exit 1
fi

print_info "All builds successful! Now trying full deployment..."

# Now try the full up command
print_info "Starting services..."
$DOCKER_CMD up -d

if [ $? -eq 0 ]; then
    print_info "Services started successfully!"
    print_info "Frontend: http://localhost"
    print_info "Backend API: http://localhost:8000/api/"
    print_info ""
    print_info "To stop services: $DOCKER_CMD down"
    print_info "To view logs: $DOCKER_CMD logs -f"
else
    print_error "Failed to start services"
    exit 1
fi