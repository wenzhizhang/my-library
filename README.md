# My Library

个人图书管理系统，支持 ISBN 扫码入库、豆瓣数据自动填充、多维度统计与可视化。

---

## 功能

- **图书管理**: 全字段录入（35+ 字段），支持搜索、筛选、排序、分页、数据导出
- **ISBN 入库**: 输入 ISBN 自动查找（用户库 → 共享库 → 豆瓣/OpenLibrary），支持表单自动填充
- **共享数据库 (root.db)**: 作者/出版社/品牌/分类/系列/图书的公共参考数据跨用户共享，创建时自动同步
- **导出功能**: 支持 SQL / CSV / Excel / Markdown / JSON 五种格式，可按表选择导出范围
- **个性化背景图**: 用户可在「设置」中从背景图列表选择个人背景，仅对当前账号生效；背景图列表由 `backend/config/backgrounds.json` 配置，热更新无需重建镜像/容器

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Python 3.12, FastAPI 0.104, SQLAlchemy 2.0, SQLite (shared root.db + per-user DBs) |
| 前端 | React 19, react-router-dom v7, recharts, axios |
| 反向代理 | Nginx (TLS 终端 + 静态文件 + API 代理) |
| 容器化 | Docker Compose |

---

## 快速开始

### 1. 环境要求

- Docker 24+ 及 Docker Compose v2
- 豆瓣 API Key（可选，用于 ISBN 自动填充）

### 2. 克隆仓库

```bash
git clone <repo-url>
cd my-library
```

### 3. 配置环境变量

```bash
# 豆瓣 API Key（可选，不设置则跳过豆瓣数据获取）
echo "DOUBAN_KEY=你的豆瓣API密钥" >> .env
```

### 4. 构建并启动

```bash
./build.sh up
```

访问 `http://localhost/my-library`。

### 5. 创建用户

首次使用需注册账户：

```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "your-password"}'
```

然后在前端登录页使用该账号登录。

### 其他命令

```bash
./build.sh up --no-cache   # 强制全量重建
./build.sh push             # 构建并推送镜像到腾讯云 CCR（自动递增版本号）
./build.sh help             # 查看帮助
```

---

## 项目结构

```
my-library/
├── backend/                   # FastAPI 后端
│   ├── config/                # 热加载配置文件
│   │   ├── projects.json      # 项目列表
│   │   └── purchase_stores.json  # 购买渠道
│   ├── models/                # SQLAlchemy 模型
│   ├── routers/               # API 路由
│   │   ├── author.py          # 作者 CRUD
│   │   ├── book.py            # 图书 CRUD + 搜索/筛选
│   │   ├── publisher.py       # 出版社/品牌 CRUD
│   │   ├── category.py        # 分类 CRUD
│   │   ├── bookshelf.py       # 书架 CRUD
│   │   ├── series.py          # 系列 CRUD
│   │   ├── book_collection.py # 书单 CRUD
│   │   ├── reading_plan.py     # 阅读计划 CRUD
│   │   ├── isbn.py            # ISBN 查询 (用户库 → root.db → 豆瓣/OpenLibrary)
│   │   ├── export.py          # 数据导出 + 批量同步到 root.db
│   ├── services/              # 业务逻辑
│   │   └── sync_to_root.py    # root.db 同步服务（增量/全量）
│   ├── tests/                 # 后端测试 (pytest, 105 用例)
│   ├── main.py                # 应用入口
│   └── Dockerfile
├── frontend/                  # React 前端
│   ├── src/
│   │   ├── components/        # 页面组件
│   │   │   ├── Books.js       # 图书列表
│   │   │   ├── BookFormPage.js   # 图书创建/编辑
│   │   │   ├── BookDetails.js    # 图书详情
│   │   │   ├── Authors.js        # 作者列表+CRUD
│   │   │   ├── Publisher*.js     # 出版社
│   │   │   ├── Brand*.js         # 品牌
│   │   │   ├── Category*.js      # 分类
│   │   │   ├── Bookshelf*.js     # 书架
│   │   │   ├── Series*.js        # 系列
│   │   │   ├── BookCollection*.js # 书单
│   │   │   ├── ReadingPlan*.js     # 阅读计划
│   │   │   ├── StatsPage.js      # 统计看板
│   │   │   └── SearchableSelect.js # 可搜索下拉
│   │   ├── __tests__/            # 前端测试 (Jest, 40 用例)
│   │   └── AuthContext.js        # 认证上下文
│   └── Dockerfile
├── nginx/                     # Nginx 反向代理
│   ├── nginx.conf             # 主配置 (TLS + 站点)
│   ├── start.sh               # 启动脚本 (动态生成站点配置)
│   └── Dockerfile
├── docker-compose.yml         # 本地开发编排
├── docker-compose.prod.yml    # 生产环境编排
├── build.sh                   # 构建/部署脚本
└── .env                       # 环境变量 (豆瓣 Key 等)
```

---

## 配置

### 豆瓣 API

```bash
# .env 文件
DOUBAN_KEY=0ac44ae01...
```

设置后，在创建图书时输入 ISBN 并点击 **Lookup**，系统自动从豆瓣获取：
- 书名、作者、出版社、出版日期
- 页数、价格、装帧类型
- 豆瓣评分、封面图片、目录
- 内容简介、作者介绍
- 标签

### 购买渠道

编辑 `backend/config/purchase_stores.json`，重启后端即可生效：

```json
{
  "purchase_stores": [
    "京东自营", "当当自营", "孔夫子旧书网", ...
  ]
}
```

该文件通过 Docker Volume 挂载，修改后 `docker compose restart backend` 即可生效，无需重建容器。

### ISBN 查询源

系统按以下顺序查询 ISBN 信息：

1. **豆瓣 API** (`DOUBAN_KEY` 必需)
2. **Open Library** (免费，无需 Key)
3. **Google Books** (`GOOGLE_BOOKS_API_KEY` 可选)

---

## API 概览

| 前缀 | 说明 |
|---|---|
| `/api/auth` | 注册、登录、Token 刷新 |
| `/api/books` | 图书 CRUD、搜索 (`?q=`)、购买日期筛选 |
| `/api/authors` | 作者 CRUD、搜索、国籍/朝代列表 |
| `/api/publishers` | 出版社 CRUD、搜索 |
| `/api/brands` | 品牌 CRUD、搜索 |
| `/api/categories` | 分类 CRUD（支持父子层级）、搜索 |
| `/api/bookshelves` | 书架 CRUD、搜索 |
| `/api/series` | 系列 CRUD、搜索 |
| `/api/reading-plans` | 阅读计划 CRUD、搜索、图书关联、进度计算 |
| `/api/book-collections` | 书单 CRUD、搜索 |
| `/api/export/` | 数据导出 (SQL/CSV/Excel/Markdown/JSON) |
| `/api/export/sync-to-root` | 批量同步到共享 root.db (支持增量/全量) |
| `/api/isbn/{isbn}` | ISBN 查询 (用户库 → root.db → 豆瓣 → OpenLibrary) |
完整 API 文档见 `backend/spec/openapi.yaml`。

---

## 测试

```bash
# 后端测试 (105 用例)
cd backend && python3 -m pytest tests/ -v

# 前端测试 (40 用例)
cd frontend && npx react-scripts test --watchAll=false
```

---

## 生产部署

### 1. 确保 `.env` 配置正确

```bash
# .env
DOUBAN_KEY=你的密钥
SERVER_IP=你的服务器IP
```

### 2. 构建并推送镜像

```bash
./build.sh push
```

### 3. 在服务器上拉取并启动

```bash
docker compose -f docker-compose.prod.yml up -d
```

### 4. TLS 证书
- **用户数据库**: 每个用户独立的 SQLite 文件 (`{uuid}.db`)，存放在 `backend/data/`
- **共享数据库**: `root.db` 存储公共参考数据（作者、出版社等），跨用户共享，创建/更新时自动同步
- **认证数据库**: `auth.db` 存储用户账号和 JWT 凭证
- 所有数据库通过 Docker Volume `my-library-data` 持久化
## 数据存储

- 每个用户使用独立的 SQLite 数据库文件，存放在 `backend/data/` 目录
- 数据库通过 Docker Volume `my-library-data` 持久化
- 配置文件通过 Volume 挂载，支持热更新
