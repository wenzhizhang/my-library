# Simple Docker Build Test Script (PowerShell)
# This script tests basic Docker functionality without complex configurations

param(
    [switch]$Help
)

# Colors for output
$Green = "Green"
$Yellow = "Yellow"
$Red = "Red"
$Cyan = "Cyan"

function Write-ColorOutput {
    param(
        [string]$Color,
        [string]$Message
    )
    Write-Host "[$Color] $Message" -ForegroundColor $Color
}

function Write-Info {
    param([string]$Message)
    Write-ColorOutput $Green "INFO" -NoNewline
    Write-Host " $Message"
}

function Write-Warning {
    param([string]$Message)
    Write-ColorOutput $Yellow "WARNING" -NoNewline
    Write-Host " $Message"
}

function Write-Error {
    param([string]$Message)
    Write-ColorOutput $Red "ERROR" -NoNewline
    Write-Host " $Message"
}

function Show-Help {
    Write-Host "Simple Docker Build Test Script" -ForegroundColor $Cyan
    Write-Host ""
    Write-Host "This script tests basic Docker functionality without complex configurations."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\test-build.ps1 [-Help]"
    Write-Host ""
    Write-Host "The script will:"
    Write-Host "  1. Check Docker installation"
    Write-Host "  2. Test docker compose vs docker-compose"
    Write-Host "  3. Build backend service"
    Write-Host "  4. Build frontend service"
    Write-Host "  5. Start all services"
}

if ($Help) {
    Show-Help
    exit 0
}

Write-Info "Testing Docker environment..."

# Check if Docker is installed
try {
    $dockerVersion = docker --version 2>$null
    Write-Info "Docker version: $dockerVersion"
} catch {
    Write-Error "Docker is not installed or not in PATH"
    exit 1
}

# Test docker compose vs docker-compose
$DOCKER_CMD = $null
try {
    $composeVersion = docker compose version 2>$null
    Write-Info "Using 'docker compose' (new syntax)"
    $DOCKER_CMD = "docker compose"
} catch {
    try {
        $composeVersion = docker-compose version 2>$null
        Write-Info "Using 'docker-compose' (legacy syntax)"
        $DOCKER_CMD = "docker-compose"
    } catch {
        Write-Error "Neither 'docker compose' nor 'docker-compose' found"
        exit 1
    }
}

Write-Info "Testing basic Docker functionality..."

# Try to build just the backend service
Write-Info "Building backend service..."
try {
    Invoke-Expression "$DOCKER_CMD build backend"
    Write-Info "Backend build successful!"
} catch {
    Write-Error "Backend build failed: $($_.Exception.Message)"
    exit 1
}

# Try to build just the frontend service
Write-Info "Building frontend service..."
try {
    Invoke-Expression "$DOCKER_CMD build frontend"
    Write-Info "Frontend build successful!"
} catch {
    Write-Error "Frontend build failed: $($_.Exception.Message)"
    exit 1
}

Write-Info "All builds successful! Now trying full deployment..."

# Now try the full up command
Write-Info "Starting services..."
try {
    Invoke-Expression "$DOCKER_CMD up -d"
    Write-Info "Services started successfully!"
    Write-Info "Frontend: http://localhost"
    Write-Info "Backend API: http://localhost:8000/api/"
    Write-Info ""
    Write-Info "To stop services: $DOCKER_CMD down"
    Write-Info "To view logs: $DOCKER_CMD logs -f"
} catch {
    Write-Error "Failed to start services: $($_.Exception.Message)"
    exit 1
}