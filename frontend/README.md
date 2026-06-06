# My Library — Semantic Search (RAG)

基于 **sqlite-vec** + **BGE 嵌入模型**的混合检索系统，在个人图书管理基础上提供语义搜索能力。

## 架构

```
用户查询 (前端 React)
    │  POST /api/rag/search
    ▼
FastAPI 后端
    │
    ├── BGE-small-zh-v1.5 (ONNX) → 512-dim 向量
    │      ↑ fastembed / 惰性加载
    │
    ├── sqlite-vec kNN          ← 语义相似度
    │
    ├── FTS5 BM25               ← 关键词精确匹配
    │
    └── 加权合并 (α 可调)       ← 最终排序
```

**嵌入模型**：`BAAI/bge-small-zh-v1.5`（33MB, 512维, 中英双语, ONNX 推理）
**向量存储**：`sqlite-vec`（SQLite 扩展，沿用每用户独立 DB 的多租户架构）
**检索策略**：混合检索（向量 + FTS5 关键词），α 参数调节两路权重

---

## 快速开始

### 1. 环境变量

首次运行需要下载嵌入模型（~33MB）。如果 HuggingFace 不可达，设置镜像：

```bash
export HF_ENDPOINT=https://hf-mirror.com
```

### 2. 安装依赖（后端）

```bash
pip install sqlite-vec fastembed
```

Docker 部署时，确保 `Dockerfile` 中包含上述依赖并设置 `HF_ENDPOINT` 环境变量。

### 3. 初始化索引

启动后端后，首次使用需要建立索引：

```bash
# 查看状态
curl http://localhost:8080/api/rag/status

# 全量重建（2616 本约 260 秒）
curl -X POST http://localhost:8080/api/rag/reindex
```

索引会自动保持同步：创建/更新/删除图书时，后台自动调用索引钩子。

### 4. 前端搜索

导航栏点击 **AI Search** 进入语义搜索页面：

1. 输入自然语言查询（中英文均可）
2. 拖拽 **Meaning** 滑块调节搜索模式（偏关键词 or 偏语义）
3. 按 Enter 或点击 Search
4. 点击结果卡片跳转图书详情

---

## API 参考

### 混合搜索

```bash
POST /api/rag/search
Content-Type: application/json

{
  "query": "关于人工智能的科幻小说",
  "top_k": 10,
  "alpha": 0.5
}
```

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `query` | string | — | 搜索查询（中英文） |
| `top_k` | int | 10 | 返回结果数量 (1-100) |
| `alpha` | float | 0.5 | 向量权重 (0=纯FTS5, 1=纯向量) |

### 索引状态

```bash
GET /api/rag/status
```

返回 `{ indexed_count, total_books, model_loaded }`。

### 全量重索引

```bash
POST /api/rag/reindex
```

返回 `{ total, indexed, failed }`。

---

## 索引同步

| 事件 | 行为 |
|------|------|
| 创建图书 | 自动生成嵌入 + FTS5 索引 |
| 更新图书 | 重新生成嵌入并更新索引 |
| 删除图书 | 清除对应向量和 FTS5 记录 |

索引在业务事务外执行（异常不影响主流程），通过 `rag.pipeline.sync_book()` / `remove_book()` 注入。

---

## 已知限制

| 限制 | 说明 |
|------|------|
| **FTS5 中文分词** | SQLite FTS5 默认 tokenizer 不支持中文分词。纯 FTS5 模式（alpha=0）对中文短语效果有限，建议使用混合模式（alpha≥0.5） |
| **重索引速度** | 单条 ~100ms（含向量推理），2616 本全量约 260 秒。可分批执行或后台异步 |
| **模型冷启动** | 首次搜索触发模型加载约 2-12 秒（含第一次下载），后续搜索正常 |

---

## 开发

### 文件结构

```
backend/
  rag/
    __init__.py       模块入口
    embedding.py      fastembed 封装（惰性加载 BGE 模型）
    vector_store.py   vec0 + FTS5 表管理、CRUD、搜索
    document.py       Book → 结构化文档构建
    pipeline.py       索引同步（sync / remove / reindex）
  routers/
    rag.py            API 端点（search / reindex / status）
  schemas/
    rag.py            请求/响应模型

frontend/
  src/components/
    RagSearch.js      搜索 UI 组件
    RagSearch.css     搜索页样式
```

### 数据流

```
Book CRUD → pipeline.sync_book()
    → build_book_document(book)     # 结构化文本
    → embed_text(document)          # BGE 512-dim 向量
    → upsert_book_vector()          # vec0 DELETE + INSERT
    → upsert_book_fts()             # FTS5 contentless
```

---

## RAG_DESIGN.md

完整的方案设计文档见项目根目录 [`RAG_DESIGN.md`](../RAG_DESIGN.md)。
