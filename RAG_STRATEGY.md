# RAG 检索策略与实现说明

> 本文档描述 My Library 的 RAG（检索增强生成）模块的完整设计、当前实现中的已知瓶颈，以及改进方向。

---

## 1. 架构总览

```
用户查询（如"甘道夫出自哪本书"）
        │
        ▼
  ┌─────────────┐
  │  Embedding   │  bge-small-zh-v1.5 (33MB, 512维, ONNX)
  │  模型推理     │  对查询文本生成 512 维向量
  └──────┬──────┘
         │
         ├──────────────────┐
         ▼                  ▼
  ┌──────────────┐  ┌──────────────┐
  │  Vector       │  │  FTS5         │
  │  Search       │  │  Full-Text    │
  │  (sqlite-vec) │  │  (BM25)       │
  │  kNN 语义匹配 │  │  关键词精确匹配 │
  └──────┬───────┘  └──────┬───────┘
         │                  │
         └──────┬──────────┘
                ▼
         ┌──────────────┐
         │  Hybrid Merge │  加权合并 (alpha)
         │  (加权求和)    │  默认 alpha=0.5
         └──────┬───────┘
                ▼
         ┌──────────────┐
         │  Enrich       │  补充书名、作者等元数据
         │  (元数据补充)   │
         └──────────────┘
                ▼
             结果列表
```

**文件分布**：

| 文件 | 职责 |
|------|------|
| `rag/embedding.py` | embedding 模型封装（惰性加载、批推理） |
| `rag/document.py` | Book → 结构化文档 + FTS5 字段 |
| `rag/vector_store.py` | sqlite-vec + FTS5 建表、CRUD、搜索、混合检索 |
| `rag/pipeline.py` | 索引同步 hook、全量重建、后台进度 |
| `routers/rag.py` | FastAPI 端点：search / reindex / status |
| `schemas/rag.py` | 请求/响应 Pydantic 模型 |

---

## 2. Embedding 策略

### 2.1 模型选择

| 属性 | 值 |
|------|-----|
| **模型** | `BAAI/bge-small-zh-v1.5` |
| **运行库** | `fastembed`（纯 ONNX 推理，无需 PyTorch） |
| **输出维度** | 512（ONNX 版本） |
| **模型大小** | ~33 MB |
| **语言** | 中英双语 |
| **加载方式** | 惰性（首次搜索/重建时加载，约 2-12s） |
| **批推理** | 支持（默认 batch_size=32），全量重建约 200ms/batch |

### 2.2 推理方式

```python
# 单条
embed_text("某段文本") → List[float]  # 512维

# 批量（全量重建时使用）
embed_texts(["文本1", "文本2", ...], batch_size=32) → List[List[float]]
```

### 2.3 长度处理

fastembed 的 BGE ONNX 版本支持最大 512 tokens。对于超出长度的中文文档，模型自动截断（取前 512 token）。约 750 个中文字符后内容被丢弃。

**当前没有分块（chunking）**——每本书的整个 `build_book_document()` 输出作为一整段喂入 embedding 模型。

---

## 3. Document 构建（= 当前的"分块"策略）

### 3.1 文档格式

`build_book_document()` 将一本 Book 及其关联实体组装为扁平化标签文本：

```
书名: 三体 (The Three-Body Problem)
作者: 刘慈欣 [中国·现代]
译者: 某某
出版社: 重庆出版社
品牌: 科幻世界
丛书: 三体
分类: 科幻小说 > 硬科幻
标签: 科幻, 外星文明, 物理学
简介: 文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划"红岸工程"取得了突破性进展……
概述: ...
目录: 第一章 科学边界... 第二章 三体问题...
ISBN: 9787536692930 | 版次: 第1版 | 语言: 中文 | 页数: 302
```

**全字段参与向量化和 FTS5 索引**：书名、作者、出版社、品牌、丛书、分类、标签、简介、概述、目录、ISBN 等。

### 3.2 当前是否分块？

**否**。整个文档作为一段文本嵌入一个 512 维向量。没有分段、没有重叠窗口、没有段落切割。

一本 `introduction` 为 2000 字的书，文档总长约 2500 字（~1700 token），超过 512 token 限制的部分被截断。

### 3.3 FTS5 字段映射

FTS5 索引不需要分块，因为它按字段独立索引。字段定义：

```sql
CREATE VIRTUAL TABLE book_fts USING fts5(
    book_id UNINDEXED,
    title, title_cn,          -- 书名（中/英）
    authors_text,             -- 作者名
    tags_text,                -- 标签
    introduction,             -- 简介（最长 2000 字）
    summary,                  -- 概述
    catalog,                  -- 目录
    publisher_name,           -- 出版社
    series_name,              -- 丛书名
    brand_name,               -- 品牌名
    content='',               -- contentless 模式
    prefix='2 3'              -- 2/3 字符前缀索引
)
```

FTS5 使用 `unicode61` 分词器。中文按**单字**切分（`unicode61` 的特性）。这意味着 FTS5 对中文查询没有分词概念，"甘道夫"在索引中存为三个独立的 token `甘` `道` `夫`。

---

## 4. 检索策略

### 4.1 向量搜索（语义匹配）

```sql
SELECT book_id, distance
FROM book_vectors
WHERE embedding MATCH :query_vec AND k = :top_k
```

- 距离度量：sqlite-vec 默认的余弦距离
- 评分转换：`score = 1.0 / (1.0 + distance)` → 值域 (0, 1]
- 召回数：`top_k * 2`（混合搜索时扩大一倍再合并）

### 4.2 FTS5 全文搜索（关键词匹配）

```python
def _fts_tokenize(query_text: str) -> str:
    """将中文查询拆成 3-4 字重叠分块，用 OR 组合。
    非中文文本保留原有前缀匹配行为。"""
    ...
```

**关键实现细节**：

1. 按空格分词（英文/拼音自然分割）
2. 每个词中提取 CJK（`[\u4e00-\u9fff]`）和非 CJK 部分
3. CJK 运行：
   - ≤4 字 → 整体短语匹配：`"甘道夫"`
   - >4 字 → 4 字重叠分块，步长 3：`("甘道夫出" OR "出自哪本" OR "本书")`
4. 非 CJK：
   - ≤4 字符 → 前缀匹配：`"AI"*`
   - >4 字符 → 截前 4 字符前缀匹配：`"Gener"*`
5. 纯标点符号被过滤
6. 多词时各词之间用 `AND` 连接

**示例**：

| 原始查询 | 生成 FTS5 查询 |
|----------|---------------|
| `甘道夫出自哪本书` | `("甘道夫出" OR "出自哪本" OR "本书")` |
| `甘道夫是哪本书里的` | `("甘道夫是" OR "是哪本书" OR "书里的")` |
| `甘道夫` | `"甘道夫"` |
| `刘慈欣 三体` | `"刘慈欣" AND "三体"` |
| `The Three-Body Problem` | `"The"* AND "Three-Body"* AND "Problem"*` |
| `关于AI的科幻小说` | `"关于" AND ("的科幻小" OR "小说") AND "AI"*` |

- BM25 评分
- 召回数：`top_k * 2`

### 4.3 混合合并

```python
def hybrid_search(db, query_text, top_k=10, alpha=0.5):
    vec_results = search_vectors(db, query_text, top_k=top_k * 2)
    fts_results  = search_fts(db, query_text, top_k=top_k * 2)

    # 各自归一化到 [0, 1]
    # 加权合并
    for r in vec_results:
        merged[r.book_id] = alpha * (r.score / vec_max)
    for r in fts_results:
        merged[r.book_id] += (1 - alpha) * (r.score / fts_max)

    return sorted(merged, key=-score)[:top_k]
```

- `alpha=1.0` → 纯向量搜索
- `alpha=0.0` → 纯 FTS5 搜索
- 默认 `alpha=0.5` → 等权混合

### 4.4 重排序（Re-rank）

**当前没有实现**。混合合并后的结果直接按加权得分排序返回，没有二次精排。

---

## 5. 后端同步策略

### 5.1 单书同步（CRUD Hook）

```python
def sync_book(db, book_id):
    book = load_book_with_relations(book_id)
    document = build_book_document(book)
    vector = embed_text(document)          # ONNX 推理
    upsert_book_vector(db, book.id, vector) # DELETE + INSERT
    upsert_book_fts(db, book.id, **fts_fields) # INSERT OR REPLACE
```

在 `routers/book.py` 的 create/update/delete 中注入。

### 5.2 全量重建

```
POST /api/rag/reindex
```

三阶段执行：
1. **Phase 1**: 遍历所有书籍，构建文档 + FTS 字段（无推理，快）
2. **Phase 2**: 批量 ONNX 推理（batch_size=32，~4 分钟/2616 本）
3. **Phase 3**: 逐条写入向量表 + FTS5 表

后台异步线程运行，轮询 `GET /api/rag/status` 查看进度。

---

## 6. 已知问题与根因分析

### 6.1 中文查询语义匹配差

**现象**：搜"甘道夫出自哪本书"，20 个结果中一个魔戒都没有。

**根因链条**：

```
问题1: 无分块（chunking）
       ↓
整个文档（含 2000 字简介）被压缩为单一 512 维向量
       ↓
问题2: 小模型容量不足
       ↓
bge-small-zh-v1.5 (33MB) 对"甘道夫"→"魔戒"的
语义关联信号弱，难以从 2000 字简介中提取关键信号
       ↓
FTS5 本该补位，但……
       ↓
问题3: CJK 分块策略脆弱
       ↓
"甘道夫出自哪本书" → 分块为 ("甘道夫出" OR "出自哪本" OR "本书")
"甘道夫出" 几乎不可能出现在任何简介中（简介写的是"甘道夫是……"不是"甘道夫出……"）
FTS5 返回 0 条相关结果
       ↓
问题4: 混合合并被噪声支配
       ↓
FTS5 的常见字分块（如"本书"）分数很高但完全不相关，
与向量弱信号等权合并后，前 20 名被无关结果占据
```

**具体数据推演**（`alpha=0.5`）：

| 书 | 向量分(归一化) | vec贡献 | FTS5分(归一化) | FTS贡献 | 总分 |
|-----|----------|----------|----------|----------|------|
| 魔戒 | 0.7 | 0.35 | 0 (无分块命中) | 0 | 0.35 |
| 某无关书 | 0.3 | 0.15 | 0.9 (命中"本书") | 0.45 | 0.60 ✅ |

### 6.2 FTS5 CJK 分块的边界问题

| 查询 | 分块 | 能否命中含甘道夫的简介 |
|------|------|---------------------|
| `甘道夫是哪本书里的` | `"甘道夫是" OR "是哪本书" OR "书里的"` | ✅ "甘道夫是"大概率命中 |
| `甘道夫出自哪本书` | `"甘道夫出" OR "出自哪本" OR "本书"` | ❌ "甘道夫出"几乎不出现 |
| `甘道夫` | `"甘道夫"` | ✅ 精确 3 字短语匹配 |

两个语义相同的查询，FTS5 结果天差地别。这是固定步长分块的固有缺陷。

### 6.3 缺少重排序

没有交叉编码器（cross-encoder）或任何形式的二次精排。初级混合的 top-20 就是最终结果，错误累积无法纠正。

---

## 7. 改进路线

### P0 — 快速见效

1. **嵌入模型升级**：`bge-small-zh-v1.5`(33MB) → `bge-base-zh-v1.5`(110MB) 或 `bge-large-zh-v1.5`(335MB)，更大的模型对中文化名/实体关联有更好的理解
2. **alpha 调优**：中文查询默认降低 FTS5 权重（如 `alpha=0.7`），减少常见字噪声

### P1 — 结构改进

3. **引入分块（chunking）**：将 2000 字简介按段落或固定窗口（256 tokens）分段嵌入，存储为多条向量（1 本书 → N 个向量 + 1 个 FTS5 条目）
4. **FTS5 查询优化**：弃用固定步长分块，改为按 CJK 字符直接做 `NEAR` 邻近查询，或引入 jieba 分词

### P2 — 精排

5. **交叉编码器重排序**：取 top-100 后用 `bge-reranker-v2-m3` 或 `ms-marco-MiniLM` 二次打分
6. **混合权重动态调整**：根据查询类型（中文/英文/混合）自动选择 `alpha`

### P3 — 评估

7. **建立测试集**：人工标注 30-50 条 query → 期望 book_id
8. **加入 CI**：每次部署自动跑 recall@k 指标
