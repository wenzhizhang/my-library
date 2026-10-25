# My Library RAG 最终设计方案

> 状态：本次 RAG 改造完成后的最终方案。
>
> 适用范围：`backend/rag/`、`backend/routers/rag.py`、`backend/schemas/rag.py`、`backend/tests/`。
>
> 接口契约以 [`backend/spec/openapi.yaml`](backend/spec/openapi.yaml) 为准；问题分析与评测细节见 [`RAG_STRATEGY.md`](RAG_STRATEGY.md)。

## 1. 目标与约束

### 1.1 要解决的问题

1. 中文图书检索召回差：书名、作者、实体名在长简介中被稀释。
2. 精确查询和语义查询互相干扰：ISBN/作者名需要 FTS，自然语言需要向量。
3. 整本简介超过 BGE 512 token，关键信息容易被截断。
4. 新增一本书不应该重建整个书库。
5. 不引入新的基础设施，不改变前端，不破坏每用户 SQLite 隔离。

### 1.2 设计约束

| 约束 | 影响 |
|------|------|
| 每用户独立 SQLite（`<uuid>.db` / `demo.db`） | 索引必须落在用户自己的 DB 内 |
| 已使用 `sqlite-vec` + FTS5 | 不迁移到外部向量数据库 |
| FastAPI 单进程 + Docker Compose | 后台任务用线程，不引入 Celery/Redis |
| 个人图书库，单用户约 1 万册 | 不需要分布式索引 |
| 中英双语 | 检索和分词必须同时覆盖 CJK 与 Latin |

### 1.3 设计意图摘要

这次改造的核心意图不是“把 RAG 做得更复杂”，而是：

- **用混合检索解决“精确 vs 语义”**：FTS5 保精确，向量保语义，RRF 做融合。
- **用摘要解决长文本截断**：摘要作为整本书的语义代表，FTS 仍保留完整字段。
- **用增量状态解决新书成本**：新书只处理新书，旧书不重复 embedding。
- **把失败变成可重试状态**：摘要失败不写入错误结果，而是留空等待下次重试。
- **保持数据可迁移**：模型缓存、向量、摘要、增量状态都能随 DB/volume 搬到云服务器。

---

## 2. 最终架构

```
                    ┌──────────────────────────────┐
                    │          Book CRUD            │
                    │  create / update / delete     │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │        rag.pipeline           │
                    │  sync_book / remove_book      │
                    │  reindex_all / start_reindex  │
                    └───────┬───────────────┬───────┘
                            │               │
              ┌─────────────▼──────┐  ┌─────▼──────────────┐
              │   book_fts (FTS5)  │  │ book_vectors (vec0)│
              │   完整字段 + CJK    │  │ 512 维 BGE 向量     │
              └─────────────┬──────┘  └─────┬──────────────┘
                            │               │
                            └───────┬───────┘
                                    ▼
                         ┌────────────────────┐
                         │   hybrid_search     │
                         │   weighted RRF      │
                         └─────────┬──────────┘
                                   │
                                   ▼
                         ┌────────────────────┐
                         │ enrich + diversify  │
                         │ 过滤 + 版本去重      │
                         └─────────┬──────────┘
                                   │
                                   ▼
                              搜索结果

摘要链路：
books.introduction ──> generate_book_summary ──> books.summary
                                                   │
                          ┌────────────────────────┘
                          ▼
              POST /api/rag/summarize/{book_id}
                          │
                          ▼
              标记 rag_book_state 待重建
                          │
                          ▼
          POST /api/rag/reindex?incremental=true
```

### 2.1 模块职责

| 文件 | 职责 |
|------|------|
| `rag/embedding.py` | fastembed/BGE 惰性加载、单条/批量推理 |
| `rag/document.py` | Book → 向量文档、FTS5 字段 |
| `rag/vector_store.py` | vec0 + FTS5 + `rag_meta` + `rag_book_state` 建表、CRUD、混合检索 |
| `rag/pipeline.py` | CRUD hook、摘要生成、全量/增量 reindex、后台进度 |
| `rag/summarize.py` | 离线抽取式摘要 + DeepSeek API 调用 |
| `routers/rag.py` | search / summarize / reindex / status API |
| `schemas/rag.py` | 请求/响应模型 |
| `scripts/eval_rag.py` | golden set 评测：Recall@K、Hit@K、MRR、nDCG |

---

## 3. 关键设计决策与意图

### 3.1 保留 `sqlite-vec` + FTS5，不引入外部向量库

**决定**：继续使用每用户 SQLite 内的 `book_vectors` 和 `book_fts`。

**意图**：

- 数据隔离天然成立：每个用户一个 DB，索引不会串库。
- 零额外运维：不需要 Chroma、Milvus、PostgreSQL/pgvector。
- 索引与业务数据同事务，删除/更新不容易出现“DB 已删、向量还在”。
- 1 万册以内，SQLite 的 kNN 和 FTS5 性能足够。

**代价**：没有分布式能力；库特别大时需要换方案。当前项目规模不需要。

### 3.2 混合检索：FTS5 + 向量 + 加权 RRF

**决定**：`hybrid_search` 分别取 FTS5 和向量结果，用加权 RRF 融合，默认 `alpha=0.5`。

**意图**：

- FTS5 擅长精确命中：书名、作者、出版社、ISBN、系列名。
- 向量擅长语义： “关于人工智能的科幻小说” 这类没有精确词的查询。
- BM25 分数和余弦分数不可直接比较，RRF 用排名融合，避免某一路分数尺度压倒另一路。
- `alpha` 保留可调：0 是纯 FTS，1 是纯向量。

**评测依据**（真实库，`top_k=10`）：

| alpha | Recall@10 | Hit@10 | MRR | nDCG@10 |
|-------|-----------|--------|-----|---------|
| 0.3 | 0.948 | 1.000 | 0.870 | 0.856 |
| **0.5** | **0.948** | **1.000** | **0.909** | **0.872** |
| 0.7 | 0.880 | 0.960 | 0.875 | 0.839 |

因此默认保持 `alpha=0.5`，不把向量权重调得更高。

### 3.3 CJK 逐字空格 + 查询端重叠 3-gram

**决定**：写入 FTS5 时把每个 CJK 字符用空格隔开；查询端把长 CJK 串切成重叠 3-gram，用 OR 连接。

**意图**：

- FTS5 的 `unicode61` 会把一整段中文当成一个 token，`甘道夫` 在 `睿智巫师甘道夫的到来` 里无法单独命中。
- 逐字空格后，每个汉字都是可检索 token，任意子串都能被定位。
- 查询端用重叠 3-gram，避免固定 4 字切块把实体从中间切断。
- 查询前剥离“出自哪本书 / 是谁写的 / 是什么”等疑问尾缀，避免这些高频词污染 FTS 排名。

**兼容性**：`rag_meta.fts_tokenizer = cjk_spaced_v1`。旧 FTS 表在 `ensure_rag_tables` 中自动删除重建，需要跑一次 reindex。

### 3.4 精确实体查询保护

**决定**：如果查询没有触发 OR 扩展（说明是短实体/书名类查询），且 FTS 命中数 ≥ `top_k`，则向量只用于重排 FTS 候选，不引入纯向量结果。

**意图**：

- “甘道夫出自哪本书”这类查询，FTS 已经能给出可靠候选。
- 纯向量结果里常有语义相近但不相关的书，容易把精确命中挤下去。
- 长自然语言查询仍允许向量补位，不牺牲语义召回。

**效果**：`甘道夫出自哪本书？` 返回 7 条相关结果，`魔戒` 位于第 4 位。

### 3.5 结果按“作品”去重，而不是按书名去重

**决定**：用“规范化书名 + 第一作者”分组，优先保留最短/最规范标题；不同卷/不同作品仍保留。

**意图**：

- 同一本书的套装、周年纪念版、精装版、不同版次会重复占据 top_k。
- 直接按书名去重会误伤《三体》和《三体 II》这类不同卷。
- 规范化时只去掉版次/装帧后缀，不合并不同卷。

### 3.6 向量用摘要，FTS 用完整字段

**决定**：

- 向量文档：`书名 + 作者 + 标签 + summary`；没有 summary 时回退到完整文档。
- FTS5：仍然索引 title、title_cn、authors、tags、introduction、summary、catalog、publisher、series、brand。

**意图**：

- BGE 只有 512 token，整本 2000 字简介会被截断，关键实体可能丢失。
- 按 header 预算后的摘要（通常 400-450 字）更适合作为“整本书的语义代表”，信噪比更高，也不会被 512 token 截断。
- FTS 不需要摘要，它需要完整字段做精确召回；两者职责分开。
- 没有摘要的旧书回退完整文档，保证兼容，不会因为没摘要就搜不到。
- 短简介（<400 字符）本身就是最好的整书表示，压缩成摘要反而损失信息，还多一次 LLM 调用，因此直接走完整文档回退。

### 3.7 摘要生成：DeepSeek 优先，本地兜底，失败留空

**决定**：

- 有 `DEEPSEEK_API_KEY`：调用 DeepSeek 官方 API（`deepseek-flash`，非 thinking，`temperature=0.3`，`max_tokens=1024`，60s 超时，最多 2 次）。
- 没有 Key：使用离线抽取式摘要（按词频选句，不依赖 jieba）。
- 有 Key 但调用失败：reindex 时摘要留空，不写入本地摘要，等待下次重试；单本摘要 API 返回 502，原摘要不变。
- 已有摘要默认不覆盖；`POST /api/rag/summarize/{book_id}?force=true` 可强制覆盖。
- 单本 CRUD 不调用 LLM，避免创建/更新请求被网络阻塞。
- `introduction` 短于 `MIN_SUMMARY_INTRODUCTION_CHARS`（400 字符）时不做摘要：完整文档直接 embedding，`summary` 留空并视为已完成；单本 API 对此返回 400，`?force=true` 才生成。400 = 512 token 窗口减去书名/作者/出版社/标签等字段的余量，保证回退文档不被模型截断。
- 摘要长度不是固定 500 字符：`document.summary_char_budget` 按该书 header（书名/作者/标签）实际占用，从 512 token 中扣除 tokenizer 余量（16）后得到每本书的预算，生成端按预算截断，构建 embedding 文档时再兜底裁一次——标题/作者后改过也不会让模型截掉摘要尾部。

**意图**：

- 在线时优先质量，离线时仍可用。
- 失败时不要把低质量本地摘要“永久锁死”，留空才能重试。
- 摘要生成是慢操作，不应阻塞普通图书 CRUD。
- 单本摘要 API 让用户可以在不跑全量 reindex 的情况下补一本书。

**长度**：摘要最多 500 字符，给书名/作者/标签留出 token 余量。

### 3.8 增量 reindex：每本书一个状态，而不是靠时间戳

**决定**：新增 `rag_book_state(book_id, summary_done)`。

- `summary_done=1`：摘要已完成，或本来就没有简介可生成，或简介短于 400 字符无需摘要。
- `summary_done=0` 或没有 state 行：下次增量需要处理。
- `sync_book` 只在向量与 FTS 都写成功后写入 state（摘要仍缺时写 `summary_done=0`）；写入失败或模型未加载时删掉 state，把重建留给增量 reindex。
- 摘要 API 更新摘要后删除 state 行，强制下次增量重建该书。
- reindex 成功处理后写入 `summary_done=1`；摘要或向量写入失败则保持 0 / 不写 state 行，下次重试。
- 旧库首次初始化时，已有向量 + FTS 的书自动补 `summary_done=1`，避免第一次增量变成全量。

**增量 pending 判定**：

```
缺 book_vectors 行
OR 缺 book_fts 行
OR rag_book_state.summary_done != 1
OR book_fts.summary != _space_cjk(book.summary)
```

**意图**：

- 不能只看“有没有向量”：新书创建时 `sync_book` 可能已经写了向量/FTS，但它还没有摘要，仍然需要增量处理。
- 不能靠时间戳：导入旧书、手动改 DB、时钟偏差都会让时间戳判断不可靠。
- 每本书的状态是确定性的，重启后仍然有效。
- 摘要 API 只写 `books.summary`，通过删 state 把“重建索引”交给增量 reindex，职责清晰。
- 摘要失败保持 0，让增量可以重试，而不是悄悄跳过。

**取舍**：旧库中已经索引、但摘要为空的旧书会被 bootstrap 标记为完成，增量不会重试它们；需要补旧摘要时跑一次全量 reindex。这是为了避免每次增量都重复调用 LLM。

### 3.9 全量 reindex 仍然保留

**决定**：`POST /api/rag/reindex` 默认全量；`?incremental=true` 才增量。

**意图**：

- 模型升级、FTS schema 变更、摘要策略变更时，全量是唯一可靠的重建方式。
- 增量是日常新书路径，不是替代全量。
- 全量按批 embedding，单批失败逐条重试；仍失败的书保留 FTS，向量下次再补。
- 清理孤儿索引行、持久化摘要、写入向量/FTS 都在各自短事务里提交；DeepSeek 与 ONNX 执行期间不持有任何 SQLite 事务，因此图书增删改不会被 reindex 卡住。
- 写入阶段开始前先把这些书的 state 行删掉并提交：任何一次写入失败或被跳过，书都保持“待处理”，不会被增量误判为已完成。
- 每本书写入前在同一事务内比对 `books.updated_at` 与开始嵌入前的快照，reindex 期间被编辑过的书跳过并留待下次增量，不会用旧快照覆盖新编辑。

### 3.10 模型指纹：换模型/换库时必须能发现

**决定**：在 `rag_meta` 里记录 `embedding_fingerprint = 模型名|维度|sqlite-vec 版本|fastembed 版本`。

- 新库（还没有向量）：初始化时写入当前指纹。
- 老库（已有向量但没指纹）：不当作兼容，写入 `unknown` 并告警，等全量 reindex 重写。
- 指纹不一致：启动时告警“准备用哪个模型、存储的是哪个模型”，`GET /api/rag/status` 返回 `index_model` / `index_model_matches=false`。
- 全量 reindex 成功（`failed == 0`）后才写入新指纹；部分失败不更新，避免把混合模型索引标成一致。

**意图**：不同模型的向量不可比，而且不会报错，只会检索悄悄变差。把本机 reindex 好的库复制到另一台服务器时，指纹能证明两边模型一致；模型升级后也能提示必须重建。

### 3.11 模型缓存持久化

**决定**：fastembed 缓存放到 `/app/.model_cache`，Docker volume `model-cache` 持久化。

**意图**：

- 容器重建不重新下载 91MB 模型。
- 离线/内网环境也能启动。
- 模型缓存可以直接复制到云服务器，但必须保证模型名、版本、维度一致。

**注意**：模型缓存只解决“加载模型”，不包含向量；向量在用户 DB 的 `book_vectors` 表里。

### 3.12 答案生成：门控检索 + 引用校验

**决定**：`POST /api/rag/answer`，DeepSeek Flash 生成，检索范围比 `/search` 窄得多。

- 书名直通：归一化查询命中库内书名时，只用这些书，跳过检索层（含向量）。书名直通排在系列直通之前，标题里自带系列名的单卷不会被整系列替换（“点校本二十四史修订本：史记”仍返回该卷）。比较类问题（“《沙丘》和《三体》有什么区别”）会同时命中多本书；系列/续集类问题（“沙丘系列”“续集有哪些”）不走向量直通，交给检索召回多卷。
- 多轮：带 history 时先用 Flash 把追问改写成独立查询（“作者是谁”→“沙陀往事 作者”），改写失败回退原问题；回答 prompt 附最近 4 轮。
- 候选池：检索一次返回最多 40 本（书名的同一作品所有版本都算），回答只用前 5 本；`has_more` 告诉前端还有没展示的书。
- 系列直通：查询里出现系列名（如“尚古一读有哪些书”）时直接按 `book_series` 取该系列全部书目，不依赖书名/摘要是否提到系列名。
- 作者直通：查询点名作者（“托尔金著的书”“苏轼的书”）时直接按 `book_authors` 取该作者全部著作；若查询恰好是作者名（“苏轼的书”归一化为“苏轼”），作者优先于“书名里恰含该名”的书；书名精确命中（“托尔金传”）仍走书名直通。
- 意图解析（`rag/intent.py`）：每次提问先用一次 Flash 调用判断 `{kind: author|series|title|topic, value: 实体名}`；author → `book_authors`、series → `book_series`、title → 书名直通（仅在非比较类问题中使用，避免把多书对比压成单本）、topic → 混合检索。这样“托尔金写过哪些书”“托尔金都有什么作品”这类换说法不需要命中“著/的书”等字面模式；解析失败或实体在库里查不到时回退到关键词启发式，不会比原来更差。回答 prompt 会带上意图（例如“只把该作者写的书算作其著作，不要把他人的传记/研究算进来”）。
- 同名去重排序：候选按“一个书名先占一个名额”排序，同书的不同版本排在后面；书名会先去掉“50周年纪念版/套装/修订版”等版本后缀再比较，避免两个版本挤掉其他作品。prompt 会告知“共 N 本（M 种）、只列前 K 本”，回答因此能说明还有多少种未列出，而不是把重复版本算成新书。
- 继续看更多：追问命中“还有/更多/再来/列出来/具体说说/…”（另有一次小模型意图兜底）时不再调回答模型；内容很少的追问（“还有么”“列出来”）会先按对话历史改写成带主语的查询（“列出来”→“刘慈欣的作品有哪些”），再沿用该问题的意图回到候选池，按 `seen_book_ids` 跳过已展示的书再取 5 本；池空时返回 `no_more`。
- 引用扩展：引用某本书问“这本书的作者还有哪些作品”时，不再把候选限制在被引用的一本书上，而是按 `book_authors` 扩展到该作者的全部著作；问书本身（“这本书讲了什么”“哪年出版”）时仍只用被引用的书。
- 关键词门控：否则先取 FTS 命中里 BM25 ≥ 最优 50% 的书；关键字只覆盖“字面出现”的书，因此再用余弦 ≥ 0.58 的向量命中补足（“讲天文观测的书”未必出现“天文观测”四个字）。最多 5 本。
- 为什么不用 RRF 分数做阈值：`hybrid_search` 的分数是按最高分归一化的 rank 融合值，第一名恒为 1.0；单分支命中时几乎全部 ≥0.76（挡不住），双分支命中时单分支一律 0.5（误杀）。整书向量本身的余弦对无关书就有 ~0.6，所以只能作为“补足”信号并配绝对下限。
- 引用约束：prompt 要求只使用候选、末尾用 `[book_id=N]` 标注；模型回 `NONE`、无引用或引用不在候选集内，一律返回 `no_answer`，不回答。
- 列表接口 `/search` 保持宽召回（用户自己挑），两个接口目标不同：搜索要 recall，回答要 precision。

**意图**：书库问答最贵的错误不是"没找到"，而是"用错书还答得很像"。把相关性判断交给两层——先由检索门控裁掉明显无关的书，再由模型对候选做最后一轮判断，并用引用校验兜底。

### 3.13 用 golden set 量化检索与回答质量

**决定**：`backend/scripts/rag_golden.jsonl` + `backend/scripts/eval_rag.py`，指标为 Recall@K、Hit@K、MRR、nDCG@K。

**意图**：

- 检索改动不能只靠“感觉好一点”，要有回归基线。
- 当前 25 条覆盖实体、书名、作者、卷名、主题、语义、英文查询。
- 后续建议扩充到 50-100 条，作为 CI/部署前回归。

**回答侧**：`scripts/eval_answer.py` + `scripts/rag_answer_golden.jsonl`（70 条：20 书名/换句式、15 主题、10 书库外、5 作者、6 系列、4 多书对比、5 多轮追问、5 补充书名）。

- 默认只跑**确定性门控**：`retrieve_for_answer` 的候选是否包含期望书、平均候选数——不花 API 调用，可以放 CI。
- `--live` 再调用 Flash，统计 **grounded**（引用书是否属于期望）、**no_answer accuracy**（无关/实时问题是否正确拒答）、**非法引用拒绝数**、延迟。
- 多轮条目只跑 `--live`（改写需要模型调用）；对比条目用 `expect_all`，统计多书引用覆盖率。
- `--judge` 追加 **LLM-as-judge 忠实度**：把回答拆成逐条 claim，判断每条是否被候选摘要支持，统计 claim 支持率与“含未支持 claim 的回答数”。
- 当前基线：candidate recall 1.000、grounded 1.000、no_answer 1.000、invalid citations 0、multi-source 覆盖 4/4、claim 支持率 388/439 = 0.884（24 条回答含未支持 claim）、平均 2.04s（意图解析每次多一次 Flash 调用；judge 现在会按候选池总数核验“还有 N 本”这类计数 claim，逐次有波动）。
- 报告落盘与对比（`scripts/eval_report.py`）：`--json [PATH]` 把 `{kind, created_at, stats, rows, 参数}` 写成 JSON，容器内默认 `/app/data/reports/`（数据卷持久化），宿主机默认 `backend/scripts/reports/`（gitignore）；`--compare PATH` 打印与上一份报告的逐指标 delta，方便回归监测。回答与检索两个脚本都支持。
- 评测暴露过两个真实问题：① “今天股市行情怎么样” 曾被当成可回答问题（引用了投资书），已在 prompt 规则里加入“实时/外部信息 → NONE”；② 三条主题期望过窄（清朝/理财/魔兽），按书库实际书目修正。partial 的 1-2 条来自 320 字摘要截断与 judge 严格度，属可接受波动。

---

## 4. 数据结构

### 4.1 向量表

```sql
CREATE VIRTUAL TABLE book_vectors USING vec0(
    book_id INTEGER PRIMARY KEY,
    embedding FLOAT[512]
);
```

- BGE ONNX 版本输出 512 维。
- vec0 不支持 `INSERT OR REPLACE`，更新时先 `DELETE` 再 `INSERT`。
- 维度变化时 `ensure_rag_tables` 会重建向量表，必须全量 reindex。

### 4.2 FTS5 表

```sql
CREATE VIRTUAL TABLE book_fts USING fts5(
    book_id UNINDEXED,
    title,
    title_cn,
    authors_text,
    tags_text,
    introduction,
    summary,
    catalog,
    publisher_name,
    series_name,
    brand_name,
    prefix='2 3'
);
```

- 普通 FTS5 表，不是 contentless，支持可靠的 `DELETE`/`INSERT OR REPLACE`。
- 写入时所有文本字段经过 `_space_cjk`。
- `rag_meta.fts_tokenizer` 记录编码版本，当前为 `cjk_spaced_v1`。

### 4.3 元数据表

```sql
CREATE TABLE rag_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```

关键 key：

| key | 含义 |
|-----|------|
| `fts_tokenizer` | FTS 编码版本，当前 `cjk_spaced_v1` |
| `book_state_bootstrapped` | 旧库是否已完成 `rag_book_state` 初始化 |

### 4.4 增量状态表

```sql
CREATE TABLE rag_book_state (
    book_id INTEGER PRIMARY KEY,
    summary_done INTEGER NOT NULL DEFAULT 0
);
```

| 值 | 含义 |
|----|------|
| `1` | 摘要已完成，或没有简介可生成 |
| `0` | 下次增量需要处理 |
| 无行 | 视为待处理；摘要 API 更新后也会删除该行 |

### 4.5 摘要字段

`books.summary` 是摘要的唯一持久化位置。摘要 API 只写这个字段，不直接改向量/FTS。

---

## 5. 索引与检索流程

### 5.1 单书写入：`sync_book`

```
create/update book
  -> 重新查询 Book + 关联实体
  -> build_book_embedding_document(book)   # 摘要优先，否则完整文档
  -> build_book_fts_fields(book)           # 完整字段
  -> 模型已加载？
       是：embed_text -> upsert_book_vector
       否：删除旧向量，避免指向旧文本
  -> upsert_book_fts
  -> upsert_book_state(summary_done = 有摘要 or 无简介)
  -> commit
```

**意图**：

- 普通 CRUD 后索引立即更新，搜索不会读到旧标题/旧作者。
- 模型没加载时不在请求里下载模型，避免阻塞；向量交给后续 reindex。
- 状态写入和向量/FTS 在同一个 savepoint，失败不会留下半条索引。

### 5.2 查询：`POST /api/rag/search`

```
query
  -> _strip_question_suffix        # 去掉“出自哪本书”等尾缀
  -> _fts_tokenize                 # CJK 重叠 3-gram + Latin prefix
  -> FTS5 search  +  vector search
  -> 精确实体查询且 FTS 足够？
       是：向量只重排 FTS 候选
       否：正常混合
  -> 加权 RRF
  -> enrich（过滤 archived / wishlist）
  -> diversify（规范化书名 + 第一作者）
  -> top_k
```

**意图**：

- 精确查询不被向量噪声污染。
- 长自然语言查询保留语义补位。
- 返回结果去重，避免同一本书的多个版本占满列表。

### 5.3 摘要：`POST /api/rag/summarize/{book_id}`

```
book
  -> 已有摘要且 force=false ? 直接返回 existing
  -> 无简介且需要生成 ? 400
  -> 无 Key ? 本地摘要 local
  -> 有 Key ? DeepSeek deepseek；失败 502
  -> 写入 books.summary
  -> 删除 rag_book_state 行（标记待重建）
  -> 返回 {summary, source, updated}
```

**意图**：

- 摘要生成和索引重建解耦：摘要接口快，索引交给增量 reindex。
- 失败不覆盖已有摘要。
- `source` 明确告诉调用方摘要来自哪里。

### 5.4 Reindex

**全量**：

```
ensure_rag_tables
  -> prune_missing_books
  -> 加载所有书
  -> 生成空摘要
  -> 构建文档
  -> 分批 embedding（batch_size=32，失败逐条重试）
  -> 逐本 upsert vector + FTS + state
```

**增量**：

```
ensure_rag_tables
  -> prune_missing_books
  -> 加载所有书
  -> _books_needing_index 过滤
       缺向量 / 缺 FTS / state != 1 / FTS 摘要与当前摘要不一致
  -> 只对 pending 书生成摘要、构建文档、embedding、写库
  -> 写 state
```

**意图**：

- 全量用于模型/schema/策略升级。
- 增量用于日常新书和摘要更新，避免 2600 本重复 embedding。
- 增量 `reindex_total` 是本次待处理书数，不是全书数。

---

## 6. API

### 6.1 搜索

```http
POST /api/rag/search
{
  "query": "关于人工智能的科幻小说",
  "top_k": 10,
  "alpha": 0.5,
  "min_score": 0.0
}
```

### 6.2 单本摘要

```http
POST /api/rag/summarize/{book_id}
POST /api/rag/summarize/{book_id}?force=true
Authorization: Bearer <token>
```

响应：

```json
{
  "book_id": 1,
  "summary": "……",
  "source": "existing | local | deepseek",
  "updated": true
}
```

错误：

| 状态码 | 含义 |
|--------|------|
| 400 | 没有简介，无法生成 |
| 404 | 书不存在 |
| 502 | 配置了 DeepSeek 但调用失败，原摘要不变 |

### 6.3 Reindex

```http
POST /api/rag/reindex
POST /api/rag/reindex?incremental=true
Authorization: Bearer <token>
```

- 默认全量。
- `incremental=true` 只处理新书/待重建书。
- 后台线程执行，立即返回；进度看 `/api/rag/status`。

### 6.4 状态

```http
GET /api/rag/status
```

返回 `indexed_count`、`total_books`、`model_loaded`、`reindex_status`、`reindex_indexed`、`reindex_failed`、`reindex_total`、`reindex_detail`。

---

## 7. 部署与数据复用

### 7.1 模型缓存

- 路径：`/app/.model_cache`，Docker volume `model-cache`。
- 当前模型目录：`/app/.model_cache/fast-bge-small-zh-v1.5/`，约 91MB。
- 云服务器可以直接复制该目录/volume，设置 `FASTEMBED_CACHE_PATH=/app/.model_cache`。
- 必须保持同一模型、同一维度（512）和同一 `fastembed` 版本；否则向量不可复用，需要全量 reindex。

### 7.2 用户数据库

直接复制用户 `.db` 可以复用：

- `book_vectors`
- `book_fts`
- `books.summary`
- `rag_meta`
- `rag_book_state`

注意事项：

1. 云端代码版本必须包含 `cjk_spaced_v1` 和 `rag_book_state`，否则 `ensure_rag_tables` 可能重建 FTS/向量表。
2. 云端 DB 文件名必须对应用户 UUID，否则应用不会使用该库。
3. 复制前备份云端原 DB，复制时停后端，避免写入冲突。
4. 复制后检查 `/api/rag/status` 的 `indexed_count`，并跑一次搜索。
5. 只复制 DB、不复制模型缓存时，查询 embedding 仍需要模型；云端必须能下载或已有缓存。

### 7.3 DeepSeek

DeepSeek 是官方 API，没有本地权重需要复制；云端配置 `DEEPSEEK_API_KEY` 即可生成新摘要。已有摘要和向量可以随 DB 直接复用。

---

## 8. 验收结果

| 项目 | 结果 |
|------|------|
| 后端测试 | `207 passed` |
| 真实库规模 | 2622 本书，2622 条向量，2622 条 FTS |
| 增量 bootstrap | 旧库首次初始化后 `pending=0`，不会全量重建 |
| 实体查询 | `甘道夫出自哪本书？` 返回 7 条相关结果，`魔戒` 第 4 位 |
| 检索基线（alpha=0.5） | Recall@10 `0.948`，Hit@10 `1.000`，MRR `0.909`，nDCG@10 `0.872` |
| 模型缓存 | 约 91MB，容器重建后离线加载约 0.7s |
| API 安全 | 未登录 `POST /api/rag/reindex` 返回 401 |
| OpenAPI | `/api/rag/reindex` 已包含 `incremental` 参数 |

---

## 9. 约束、风险与未做

| 项目 | 说明 |
|------|------|
| 没有分块 | 整本书仍是单向量；超长简介靠摘要缓解，未做 chunking |
| 增量判定 | 只比较索引存在性、state、摘要文本；不比较所有字段 hash。普通编辑由 `sync_book` 即时更新，直接改 DB 需要全量 reindex |
| 旧空摘要 | 旧库中已索引但摘要为空的旧书不会在增量中重试，需全量 reindex 补 |
| 模型变更 | 换模型/维度必须重建向量表并全量 reindex |
| FTS schema 变更 | `fts_tokenizer` 版本变化会重建 FTS，需要 reindex |
| DeepSeek | 有 API 成本/延迟；失败留空重试；并发 4 个 worker |
| golden set | 当前 25 条，建议扩充到 50-100 条并接入 CI |
| 前端 | 本次未改前端 |
| 部署复制 | DB 复制依赖相同代码版本和正确 UUID；模型缓存需单独复制 |

---

## 10. 关键文件

```
backend/rag/
  embedding.py       # fastembed/BGE
  document.py        # 文档 + FTS 字段
  vector_store.py    # vec0 / FTS5 / rag_meta / rag_book_state
  pipeline.py        # sync / remove / summary / reindex
  summarize.py       # 本地摘要 + DeepSeek
backend/routers/rag.py
backend/schemas/rag.py
backend/spec/openapi.yaml
backend/scripts/rag_golden.jsonl
backend/scripts/eval_rag.py
backend/tests/test_rag.py
backend/tests/test_rag_summary.py
backend/tests/test_rag_incremental.py
backend/tests/test_rag_eval.py
```

---

## 11. 设计意图总结

这套方案优先保证三件事：

1. **检索质量**：FTS5 保精确，向量保语义，RRF 融合，精确实体查询不被噪声污染。
2. **可维护性**：所有索引状态都在用户自己的 SQLite 内，没有额外服务；失败可重试，旧数据可兼容。
3. **增量成本**：新书只处理新书，摘要 API 和增量 reindex 解耦，模型缓存和 DB 都可以直接迁移到云服务器。

如果未来书库规模、查询复杂度或摘要质量要求明显上升，下一步才是 chunking、更大的 embedding 模型、cross-encoder 重排和动态 alpha；当前方案刻意没有提前引入这些复杂度。
