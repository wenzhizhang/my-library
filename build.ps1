# Docker Build Script for My Library (PowerShell)
# This script helps build Docker images with dynamic server IP configuration

param(
    [string]$ServerIP = "",
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
    Write-Host "Docker Build Script for My Library" -ForegroundColor $Cyan
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  .\build.ps1 [-ServerIP <ip>] [-Help]"
    Write-Host ""
    Write-Host "Parameters:"
    Write-Host "  -ServerIP    Server public IP address (optional, will read from .env if not provided)"
    Write-Host "  -Help        Show this help message"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\build.ps1"
    Write-Host "  .\build.ps1 -ServerIP 111.229.109.204"
    Write-Host "  .\build.ps1 -Help"
}

if ($Help) {
    Show-Help
    exit 0
}

# Default values
$DEFAULT_IP = "localhost"
$DOCKER_COMPOSE_FILE = "docker-compose.yml"

# Check if .env file exists
if (-not (Test-Path ".env")) {
    Write-Warning ".env file not found. Creating template..."

    $envContent = @"
# Docker Compose Environment Variables
# Set your server public IP address here
SERVER_IP=${DEFAULT_IP}

# Example:
# SERVER_IP=111.229.109.204
# SERVER_IP=dingfengbo.top
"@

    $envContent | Out-File -FilePath ".env" -Encoding UTF8
    Write-Info "Created .env file with default SERVER_IP=${DEFAULT_IP}"
    Write-Warning "Please edit .env file and set your SERVER_IP before deployment!"
}

# Load environment variables from .env file
if (Test-Path ".env") {
    $envVars = Get-Content ".env" | Where-Object { $_ -notmatch '^#' -and $_.Trim() -ne '' }
    foreach ($line in $envVars) {
        if ($line -match '(.+?)=(.+)') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            Set-Variable -Name $key -Value $value -Scope Script
        }
    }
}

# Use parameter if provided, otherwise use from .env
if ($ServerIP) {
    $env:SERVER_IP = $ServerIP
} elseif (-not $env:SERVER_IP -or $env:SERVER_IP -eq "your-server-public-ip") {
    Write-Warning "SERVER_IP not set or using placeholder value."
    Write-Warning "Using default: ${DEFAULT_IP}"
    $env:SERVER_IP = $DEFAULT_IP
}

Write-Info "Building with SERVER_IP=$($env:SERVER_IP)"

# Build and run
Write-Info "Building Docker images..."
try {
    # Try docker compose first (newer versions)
    $dockerCommand = "docker compose --env-file .env up --build"
    Write-Info "Running: $dockerCommand"
    Invoke-Expression $dockerCommand
} catch {
    try {
        # Fallback to docker-compose (older versions or WSL)
        Write-Warning "docker compose failed, trying docker-compose..."
        $dockerCommand = "docker-compose --env-file .env up --build"
        Write-Info "Running: $dockerCommand"
        Invoke-Expression $dockerCommand
    } catch {
        Write-Error "Both docker compose and docker-compose failed. Please check your Docker installation."
        Write-Error "Error: $($_.Exception.Message)"
        exit 1
    }
}