# My Library RAG 设计方案

## 1. 现状分析

### 项目背景

[My Library](https://github.com/dingfengbo/my-library) 是一个个人图书管理系统，后端 Python FastAPI + SQLite（多租户，每用户独立 DB），前端 React，部署在 Docker Compose。

### 可检索文本字段

| 实体 | 文本字段 | 类型 | 最大长度 |
|------|----------|------|----------|
| **Book** | title, title_cn, translator, catalog, introduction, summary, tags, edition, printing_info | 结构化描述 | catalog/intro/summary 各 2000 chars |
| **Author** | name, name_cn, nation, dynasty, intro | 元数据 | intro 1000 chars |
| **Publisher** | name, intro | 元数据 | intro 1000 chars |
| **Brand** | name, intro | 元数据 | intro 1000 chars |
| **Category** | name, path | 分类路径 | path 500 chars |
| **BookSeries** | name, intro | 元数据 | intro 1000 chars |
| **Bookshelf** | name, intro | 元数据 | intro 1000 chars |

### 架构约束

- **多租户 SQLite**：每用户独立 DB 文件（`<uuid>.db` / `demo.db`），数据天然隔离
- **后端**：单进程 FastAPI，无外部依赖如 Redis/PostgreSQL
- **部署**：Docker Compose，数据持久化 `my-library-data` volume
- **现有搜索**：仅 SQL `LIKE`/`=` 过滤，无全文检索
- **数据量级**：个人图书管理，单用户 ≤ 1 万册
- **语言**：中英双语混合

---

## 2. 技术选型

### 2.1 向量存储 → `sqlite-vec`

**选型理由**：

- 直接利用已有**每用户独立 SQLite DB**，每用户一个向量表，数据隔离不变
- SQLite 扩展（.so 加载），`CREATE VIRTUAL TABLE vec_books USING vec0(...)` 即可使用
- 支持 kNN 查询（`ORDER BY distance LIMIT N`），向量维度 ≤ 1024
- 无需额外服务，零运维成本，Docker 部署零改动
- 1 万条级别性能足够（单次搜索 < 10ms）

**对比淘汰**：

| 方案 | 淘汰原因 |
|------|----------|
| ChromaDB | 额外进程 + 网络开销，破坏"单进程单DB"的简洁性 |
| FAISS | 脱离 SQLite 事务，需额外持久化层 |
| pgvector | 需要从 SQLite 迁移到 PostgreSQL，改动太大 |

### 2.2 嵌入模型 → `BAAI/bge-small-zh-v1.5`（通过 `fastembed`）

**选型理由**：

- **中英双语**：BGE 系列同时在中英文上训练，匹配本项目双语数据
- **小体积**：33MB（vs `bge-base-zh` 110MB）
- **512 维**（ONNX 版本）：在 `sqlite-vec` 1024 维上限内
- **MIT 许可证**
- **`fastembed` 封装**：纯 ONNX 推理，无需 torch（避免 ~800MB 镜像膨胀）

> ⚠️ 实际部署时，`fastembed` 的 ONNX 版本输出为 **512 维**（非原始 PyTorch 的 384 维）。需要设置 `HF_ENDPOINT=https://hf-mirror.com` 环境变量以使用国内镜像下载模型。

### 2.3 检索策略 → 混合检索（Hybrid Search）

```
用户查询
    │
    ├──→ FTS5 关键词检索（精确匹配：书名、作者名、ISBN、标签）
    │        ↑ SQLite 内置，零额外依赖
    │
    └──→ 向量相似度检索（语义匹配："关于AI的科幻书"）
             ↑ BGE embedding + vec0 kNN
    │
    └──→ 加权合并（Weighted Sum）
             ↑ 参数 α 控制向量权重 (0=纯FTS5, 1=纯向量)
```

**为什么保留 FTS5**：

- ISBN 精确查找、作者朝代、出版社名 — 语义搜索不擅长精确匹配
- FTS5 是 SQLite 内置模块，零额外依赖，索引与数据在同一事务中

### 2.4 集成方式 → 内置模块（非独立服务）

```
backend/
  rag/
    __init__.py       # 模块入口
    embedding.py      # fastembed 封装（惰性加载）
    vector_store.py   # sqlite-vec 表管理、CRUD、搜索
    document.py       # Book → 结构化文档 + FTS5 字段提取
    pipeline.py       # 索引同步 hook（sync/remove/reindex）
```

无需新建 Docker 服务，直接在现有 FastAPI 进程中运行。

---

## 3. 数据结构设计

### 3.1 SQLite 扩展表（每用户 DB 内创建）

```sql
-- 向量表（sqlite-vec 虚表）
CREATE VIRTUAL TABLE book_vectors USING vec0(
    book_id INTEGER PRIMARY KEY,   -- references books.id
    embedding FLOAT[512]            -- BGE 512 维向量（ONNX）
);

-- FTS5 全文索引表（contentless 模式）
CREATE VIRTUAL TABLE book_fts USING fts5(
    book_id UNINDEXED,
    title, title_cn,
    authors_text,
    tags_text,
    introduction, summary, catalog,
    publisher_name, series_name,
    brand_name,
    content='',
    prefix='2 3'
);
```

> ⚠️ **vec0 upsert 限制**：vec0 虚表不支持 `INSERT OR REPLACE`，更新向量时需要先 `DELETE` 再 `INSERT`。
> ⚠️ **FTS5 contentless 删除**：`content=''` 表不支持 `DELETE FROM`，必须使用 `INSERT INTO ... VALUES('delete', :book_id)` 语法。

### 3.2 文档构建格式

每条 Book 构建为一个扁平文本，用于生成嵌入向量：

```
书名: 《三体》
英文: The Three-Body Problem
作者: 刘慈欣 [中国] | dynasty: 现代
出版社: 重庆出版社
品牌: 科幻世界
分类: 科幻小说 > 硬科幻
标签: 科幻, 外星文明, 物理学
简介: 文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划"红岸工程"...
目录: 第一章 科学边界... 第二章 三体问题...
```

文档同时用于 FTS5 索引和向量嵌入。

---

## 4. 索引同步策略

### 4.1 事件驱动（CRUD Hook）

| 事件 | 动作 |
|------|------|
| `create_book` | 构建文档 → 生成向量 → `DELETE+INSERT` vec 表 + `INSERT OR REPLACE` FTS 表 |
| `update_book` | 重新生成文档 → 新向量 → 同上 |
| `delete_book` | `DELETE` vec 表 + `INSERT ... VALUES('delete')` FTS 表 |

在 `routers/book.py` 和 `main.py` 的表单 `add_book` 中注射 hook 调用 `rag.pipeline.sync_book()`。

### 4.2 全量重建命令

```
POST /api/rag/reindex
```

- 逐条遍历 books，调用 `sync_book()` 执行 delete+insert
- 返回 `{total, indexed, failed}` 统计
- 🔴 2616 本全量重建约 260 秒（单条 ~100ms），可用于后台异步执行

---

## 5. API 设计

### 混合搜索

```
POST /api/rag/search
{
  "query": "关于人工智能的科幻小说",
  "top_k": 10,
  "alpha": 0.5
}
→
{
  "query": "关于人工智能的科幻小说",
  "total": 5,
  "results": [
    {
      "book_id": 1,
      "score": 0.92,
      "title": "三体",
      "title_cn": "三体",
      "authors": ["刘慈欣"]
    }
  ]
}
```

### 全量重索引

```
POST /api/rag/reindex
→
{
  "total": 2616,
  "indexed": 2616,
  "failed": 0
}
```

### 索引状态

```
GET /api/rag/status
→
{
  "indexed_count": 42,
  "total_books": 2616,
  "model_loaded": false
}
```

---

## 6. 约束与风险

| 项目 | 说明 |
|------|------|
| **ONNX 推理速度** | CPU 上约 50-100ms/条（单条），批量 32 条约 200ms |
| **模型加载** | 首次使用约 2-12 秒（含下载 ~33MB），之后惰性缓存 |
| **多租户隔离** | 每 DB 独立的向量表 + FTS5 表，无交叉泄露 |
| **事务一致性** | `sync_book` 在同一个 SQLAlchemy session 中执行，与业务操作原子 |
| **Docker 镜像** | 无需 torch，镜像增量 ~50MB（onnxruntime + fastembed） |
| **网络要求** | 首次需访问 HuggingFace（或配置 `HF_ENDPOINT=https://hf-mirror.com`） |
| **全量重索引** | 2616 本 ~260 秒，建议用后台任务或分批执行 |

---

## 7. 实施记录

### Phase 1 — 基础设施 ✅
- [x] 本设计文档
- [x] 安装依赖：`sqlite-vec==0.1.9`, `fastembed==0.8.0`
- [x] 创建 `backend/rag/` 模块结构（5 个文件）
- [x] 实现 `embedding.py`（fastembed 惰性加载 BGE ONNX 512-dim）
- [x] 实现 `vector_store.py`（vec0+FTS5 建表、CRUD、hybrid_search）
- [x] 修改 `database.py`：sqlite-vec 扩展通过 SQLAlchemy event 自动加载，RAG 表自动初始化

### Phase 2 — 索引同步 ✅
- [x] 实现 `document.py`：Book + 关联实体 → 结构化文档 + FTS5 字段提取
- [x] 实现 `pipeline.py`：`sync_book`（delete+insert）、`remove_book`、`reindex_all`
- [x] 注入 hooks：`routers/book.py`（create/update/delete）+ `main.py`（表单 add_book）

### Phase 3 — 搜索 API ✅
- [x] 创建 `schemas/rag.py`：请求/响应模型
- [x] 创建 `routers/rag.py`：`POST /api/rag/search`、`POST /api/rag/reindex`、`GET /api/rag/status`
- [x] 注册到 `routers/__init__.py` 和 `main.py`

### Phase 4 — 前端集成（可选）
- [ ] RAG 搜索 UI 组件
- [ ] 匹配片段高亮展示

### 实施中发现的关键修正

| # | 问题 | 修复 |
|---|------|------|
| 1 | ONNX 版本 `bge-small-zh-v1.5` 输出 512 维（非文档说的 384） | 所有 SQL schema 改为 `FLOAT[512]`，添加维度迁移检测 |
| 2 | HFC 网络不可达 | 分布式部署需设置 `HF_ENDPOINT=https://hf-mirror.com` |
| 3 | vec0 不支持 `INSERT OR REPLACE`（UNIQUE 约束） | 改为 `DELETE + INSERT` 实现 upsert |
| 4 | FTS5 contentless 表不支持 `DELETE FROM` | 改为 `INSERT INTO ... VALUES('delete', :book_id)` |
