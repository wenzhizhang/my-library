# My Library - Docker Deployment

## 动态构建Docker镜像

由于nginx配置中包含服务器IP地址，本项目支持在构建时动态设置服务器IP。

### 配置步骤

1. **设置服务器IP**
   编辑 `.env` 文件，设置你的服务器公网IP：
   ```bash
   SERVER_IP=your-server-public-ip
   ```

2. **构建和运行**

   **在WSL/Linux环境中：**
   ```bash
   # 使用bash脚本（推荐）
   ./build.sh

   # 或者手动构建
   docker compose --env-file .env up --build
   # 或（旧版本）
   docker-compose --env-file .env up --build
   ```

   **在Windows PowerShell中：**
   ```powershell
   # 使用PowerShell脚本
   .\build.ps1

   # 或者手动指定IP
   .\build.ps1 -ServerIP 111.229.109.204
   ```

### Docker版本兼容性

- **Docker Desktop 20.10+**: 支持 `docker compose` (新语法)
- **旧版本Docker**: 支持 `docker-compose` (旧语法)
- **WSL环境**: 脚本会自动检测并使用正确的命令

### 故障排除

如果遇到构建卡住或 `--env-file` 不支持的问题：

1. **使用测试脚本**：
   ```bash
   # WSL/Linux
   ./test-build.sh

   # Windows PowerShell
   .\test-build.ps1
   ```

2. **手动构建**：
   ```bash
   # 构建后端
   docker compose build backend

   # 构建前端
   docker compose build frontend

   # 启动服务
   docker compose up -d
   ```

3. **如果buildx问题**，检查Docker Desktop设置或使用：
   ```bash
   DOCKER_BUILDKIT=0 docker compose build
   ```

### 当前状态

由于构建过程中遇到兼容性问题，我暂时简化了Dockerfile，移除了动态nginx配置功能。基本的服务构建和运行应该可以正常工作。

如果需要动态nginx配置，可以在容器启动后手动配置或使用其他方法。

### 工作原理

- `config/nginx.conf.template`: nginx配置模板文件，使用 `${SERVER_IP}` 占位符
- `Dockerfile`: 在构建时使用 `envsubst` 命令替换模板中的占位符
- `docker-compose.yml`: 通过构建参数传递 `SERVER_IP` 环境变量
- `.env`: 环境变量配置文件
- `build.ps1`: PowerShell构建脚本，自动处理环境变量

### 文件说明

- `config/nginx.conf.template`: nginx配置模板（保留在版本控制中）
- `config/nginx.conf`: 生成的nginx配置文件（已添加到.gitignore）
- `.env`: 环境变量配置（需要用户编辑）
- `build.sh`: Bash构建脚本（Linux/Mac/WSL）
- `build.ps1`: PowerShell构建脚本（Windows）
- `build.sh`: Bash构建脚本（Linux/Mac）

### 部署到服务器

1. 在服务器上克隆代码
2. 编辑 `.env` 文件设置正确的服务器IP
3. 根据环境运行相应脚本：
   - WSL/Linux: `./build.sh`
   - Windows: `.\build.ps1`

### 本地开发

本地开发时可以不设置SERVER_IP，默认使用localhost：
```bash
# WSL/Linux
./build.sh

# Windows
.\build.ps1
```

### 构建脚本参数

**PowerShell脚本参数：**
```powershell
# 显示帮助
.\build.ps1 -Help

# 指定服务器IP
.\build.ps1 -ServerIP 111.229.109.204
```

**Bash脚本参数：**
```bash
# 目前不支持参数，请直接编辑 .env 文件
```