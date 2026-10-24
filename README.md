# My Library

个人图书管理系统，支持 ISBN 扫码入库、豆瓣数据自动填充、多维度统计与可视化。

---

## 功能

- **图书管理**: 全字段录入（35+ 字段），支持搜索、筛选、排序、分页、数据导出
- **ISBN 入库**: 输入 ISBN 自动查找（用户库 → 共享库 → 豆瓣/OpenLibrary），支持表单自动填充
- **共享数据库 (root.db)**: 作者/出版社/品牌/分类/系列/图书的公共参考数据跨用户共享，创建时自动同步
- **导出功能**: 支持 SQL / CSV / Excel / Markdown / JSON 五种格式，可按表选择导出范围
- **个性化背景图**: 用户可在「设置」中从背景图列表选择个人背景，仅对当前账号生效；背景图列表由 `backend/config/backgrounds.json` 配置（生产环境随镜像发布，改动后需重新构建镜像；开发环境的 compose 会挂载该目录，改完重启后端即可）

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

# DeepSeek API Key（可选，用于 reindex 时生成图书摘要；不设置则用离线摘要）
echo "DEEPSEEK_API_KEY=你的DeepSeek API密钥" >> .env
```

### 4. 构建并启动

```bash
./build.sh up
```

访问 `http://localhost/my-library`。首次启动会下载 BGE embedding 模型到
`model-cache` volume；之后重建容器不会重复下载。

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

### DeepSeek AI 摘要

`.env` 位于项目根目录（与 `docker-compose.yml` 同级）：
`/home/wenzhi/projects/my-library/.env`。

`DEEPSEEK_API_KEY` 设置后，执行 RAG reindex 时会调用 DeepSeek 官方 API
（`https://api.deepseek.com/chat/completions`）的 `deepseek-flash`
（DeepSeek V4.1 Flash），把 `introduction` 压缩成约 300-450 字的摘要，
再用摘要做 embedding。未设置 Key 时自动回退到离线抽取式摘要；设置 Key
但调用失败时保留空摘要，下次 reindex 只重试这些书。`introduction` 本身
不足 400 字符（`MIN_SUMMARY_INTRODUCTION_CHARS`）时不做摘要：这段文字
连同书名/作者/出版社等字段仍在 512 token 的 embedding 窗口内（400 就是留
给这些字段的余量），压缩只会丢信息，所以直接用完整文档做 embedding。
已有摘要不会被覆盖；单本创建/更新不会调用 LLM，避免请求被网络阻塞。

先用 20 本书试跑，只打印摘要和 token 用量，不写数据库：

```bash
docker compose exec backend python scripts/summarize_sample.py --limit 20
```

注意：如果先用未设置 Key 的方式 reindex，会写入离线摘要；之后设置 Key
时，批量 reindex 仍会保留旧摘要，需要先清空 `books.summary`。单本书可以
用下面的 API 加 `?force=true` 直接重新生成。

单独生成一本书的摘要（不需要跑完整 reindex）：

```http
POST /api/rag/summarize/{book_id}
Authorization: Bearer <token>
```

默认已有摘要会原样返回；需要重新生成时加 `?force=true`：

```bash
curl -X POST "http://localhost:8000/api/rag/summarize/123?force=true" \
  -H "Authorization: Bearer <token>"
```

返回：

```json
{
  "book_id": 123,
  "summary": "...",
  "source": "deepseek",
  "updated": true
}
```

`source` 可能是 `existing`（已有摘要）、`local`（未配置 Key，离线摘要）或
`deepseek`。简介为空、或简介短于 400 字符（默认不摘要）返回 400，此时可加
`?force=true` 强制生成；书不存在返回 404；配置了 Key 但调用失败返回 502，
并保留原摘要。这个接口只写 `books.summary`，不会自动更新向量/FTS；
需要再跑一次 reindex 才会让 RAG 索引使用新摘要。

### 增量 Reindex

新书加入后只需要跑增量，不必重建整个书库：

```http
POST /api/rag/reindex?incremental=true
Authorization: Bearer <token>
```

增量模式只处理：

- 没有向量或 FTS 记录的书
- 新书（`sync_book` 会把它标记为待生成摘要）
- 通过摘要 API 更新过摘要的书
- 上次摘要生成失败、仍待重试的书

已索引且摘要未变的旧书会跳过。旧书库里本来就没有摘要的书不会在增量时
被重新处理；需要补旧摘要时仍跑一次全量 `POST /api/rag/reindex`。首次部署
这套状态表时，已同时存在向量和 FTS 的旧书会自动标记为已完成，所以第一次
增量也不会全量重建。

进度仍通过 `GET /api/rag/status` 查看，`reindex_total` 是本次待处理的书数。

完整的数据结构、设计决策与意图见 [`RAG_DESIGN.md`](RAG_DESIGN.md)。

### RAG 召回评测

仓库自带一个可扩充的 golden set：`backend/scripts/rag_golden.jsonl`。
每行一条 query，`relevant` 写期望命中的书名（可选 `author`）：

```json
{"query": "甘道夫出自哪本书？", "relevant": [{"title": "霍比特人"}, {"title": "魔戒"}]}
```

运行评测：

```bash
docker compose exec backend python scripts/eval_rag.py \
  --db /app/data/<用户 uuid>.db \
  --top-k 10 \
  --alpha 0.5
```

脚本输出每条 query 的 Recall@K、Hit@K、MRR、nDCG，以及整体均值。
加 `--show-results` 可查看实际返回的书名。建议先人工确认/扩充到
50-100 条，再把它作为改检索逻辑后的回归基线。

### 购买渠道

编辑 `backend/config/purchase_stores.json` 后生效（开发环境挂载生效、重启后端即可；生产环境随镜像发布，需重新构建镜像）：

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
| `/api/rag` | 混合搜索、reindex、单本摘要生成 |
完整 API 文档见 `backend/spec/openapi.yaml`。

---

## 测试

```bash
# 后端测试 (207 用例)
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
