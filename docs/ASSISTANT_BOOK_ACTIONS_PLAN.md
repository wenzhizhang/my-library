# 助手录入 / 编辑图书 — 实施计划

状态：P1（录入）与 P2（编辑 / 元数据补全）均已实现。决策来源：2026-10 的 grilling 轮次（Q1–Q25）。

## 1. 目标

- **P1**：对话里按 ISBN 录入新书：抓元数据 → 可编辑草稿 → 用户确认 → 单事务写入；
  支持对话改草稿、ISBN 已存在时「更新 / 放弃 / 新增」、封面下载转存、扫码/手动兜底。
- **P2**：对话里修改已有图书（含用元数据补全缺失字段），只写变更字段并做 `updated_at` 并发校验。

### 非目标

- 不做批量导入或批量抓取；
- 不做京东抓取、当当联盟、未授权第三方「接口」；
- 不做助手自主写库：确认前不写任何图书/作者/出版社。

## 2. 已定决策

| 项 | 决策 |
|---|---|
| 写入方式 | 助手动作层只生成草稿；确认后调用现有创建/更新逻辑（P1 的 commit 内部复用同一服务） |
| 分期 | P1 录入，P2 编辑 |
| 确认交互 | 对话内草稿卡 + 卡片内编辑 + 对话改草稿；「去表单微调」兜底 |
| 重复 ISBN | 更新 / 放弃 / 新增（新增允许同 ISBN，但显著提示） |
| 信息不全 | 允许最小创建（ISBN、书名），缺失字段标「待补充」 |
| 封面 | 服务端下载转存到本地媒体目录，`thumb_image` 存本地路径 |
| 字段范围 | 全部可改；个人状态字段（购买、阅读、书架）只在明确要求时改 |
| 权限 | 仅登录用户可写；游客只读；记录操作日志 |
| 入口 | 先做对话框 + 扫码；图书页按钮后续 |
| 数据源 | 豆瓣页面抓取为主，当当搜索为回退，外文 ISBN 用 Google Books/Open Library；京东不做 |
| 抓取治理 | 每源 2.5s 间隔、5–8s 超时、403/418 冷却 10 分钟、结果缓存 30 天、来源标注 |
| 缓存 | 共享 SQLite `metadata_cache`（`DATA_DIR/metadata_cache.db`） |
| 草稿状态 | 前端持有，确认时回传，服务端重新校验 |
| 对话改草稿 | P1 支持：消息 + 草稿 → 服务端解析字段变更并返回新草稿 |
| 触发 | ISBN 正则 + 动作关键词，必须出现动作词；LLM 兜底 |
| 并发 | 编辑草稿带 `updated_at`，不一致拒绝并重新生成 |
| 失败 | 查询失败给手动录入（ISBN 预填）；提交失败保留草稿、整单回滚 |

## 3. 架构

```
Assistant 对话框
  ├─ 检测到录入/修改 → POST /api/assistant/actions {message, draft?, focus_book_id?}
  │     ├─ 无草稿：识别意图 → 元数据服务 → 草稿
  │     └─ 有草稿：解析「字段 = 值」→ 更新草稿
  ├─ 草稿卡片：字段编辑 / 冲突选择 / 对话修改 / 去表单
  └─ 确认 → POST /api/assistant/commit {draft, duplicate_choice?}
        └─ 鉴权 → 作者/出版社解析或创建 → 复用现有建书/更新逻辑 → 返回 book_id
```

### 后端模块

| 模块 | 职责 |
|---|---|
| `services/metadata/fields.py` | 规范字段、字段权威、合并、冲突、缺失 |
| `services/metadata/cache.py` | SQLite 元数据缓存（TTL 30 天） |
| `services/metadata/rate_limit.py` | 每源串行 + 最小间隔 + 冷却 |
| `services/metadata/douban.py` | `book.douban.com/isbn/<ISBN>` 解析 |
| `services/metadata/dangdang.py` | `search.dangdang.com` 搜索解析 |
| `services/metadata/foreign.py` | 外文 ISBN 回退（Open Library / Google Books） |
| `services/metadata/covers.py` | 带 Referer 下载封面 → 本地媒体目录 |
| `services/metadata/service.py` | `lookup_isbn`、组合检索、缓存编排 |
| `routers/assistant.py` | `actions`、`commit`（仅登录） |
| `routers/isbn.py` | `GET /api/isbn/{isbn}` 改用新服务；新增 `POST /api/isbn/lookup` |
| `routers/media.py` | 本地封面优先，CDN 回退 |
| `services/assistant_actions.py` | 草稿构建、对话改稿、提交、实体解析、审计 |

### 前端模块

- `components/assistant/DraftCard.js` + CSS：封面、字段、来源、缺失、冲突、重复 ISBN 三选一；
- `components/assistant/useAssistantChat.js`：动作检测、草稿状态、确认提交、对话改稿；
- `Assistant.js`：草稿消息渲染、扫码入口、去表单；
- `BookFormPage`：共用升级后的 ISBN 查询，支持草稿预填（P2）。

## 4. 数据源

| 源 | 字段 | 方式 | 限制 |
|---|---|---|---|
| 豆瓣 | 封面、简介、目录、作者、出版社、日期、评分、译者、装帧 | 页面解析；封面需 `Referer: https://book.douban.com/` | 2.5s/次，403/418 冷却 10 分钟 |
| 当当 | 价格、出版社、日期、装帧、ISBN、封面 | 搜索页 + 商品页（GB18030） | 2.5s/次 |
| Google Books / Open Library | 外文 ISBN 兜底 | 现有适配器 | 中文覆盖弱，不优先 |
| 京东 | — | 不做 | — |

**字段权威**：封面/简介/目录/作者/出版社/日期/评分 → 豆瓣；价格 → 当当；其余缺失才回退。
冲突不静默覆盖：草稿标「多源不一致」，默认权威值，允许切换。

**组合检索**：ISBN 信息不全时，用「书名 + 作者」在当当搜索；规范化书名 + 作者一致为强匹配，
出版社/年份加分；强匹配自动采用并标来源，多候选让用户选（最多 3 个）。

## 4.1 页面上下文

- 前端从路由解析详情页：`/books/:id`、`/authors/:id`、`/publishers/:id`、`/series/:id`、`/brands/:id`，
  随 `/api/rag/answer` 与 `/api/assistant/actions` 发送 `page_context: {kind, id}`。
- 图书页：问「这本书」→ 以该书为唯一候选；问「这个作者 / 出版社 / 系列 / 品牌」→ 解析该书的关联实体；
  其他普通问题不强行限定到这本书。
- 作者 / 出版社 / 系列 / 品牌页：加载实体简介与关联书目，提示词中带「当前页面信息」，
  回答实体本身时也要求引用至少一本关联书；"还有么"只补充未展示的关联书。
- 编辑器：图书页的 `page_context` 作为默认编辑目标，因此「修改这本书」无需先引用。
- 权重：页面上下文只在**进入该页面后的第一轮**随请求发送（新对话或切换页面后重置），
  之后完全由对话历史驱动；因此"作者页问托尔金 → 问红楼梦作者 → 问作者"会落到曹雪芹，
  而不是一直回到托尔金。
- 前端在面板顶部显示"正在浏览：…"提示条，第一轮后改为"后续回答优先结合对话上下文"。

## 5. 接口

### `POST /api/assistant/actions`（登录）

```json
{"message": "录入 9787020158065", "draft": null, "focus_book_id": null}
// 结构化动作：{"action": "edit_book" | "refresh_draft" | "fill_missing", "focus_book_id": 42, "draft": {...}}
```

响应：

```json
{
  "intent": "create_book | update_book | revise_draft | none",
  "draft": {
    "action": "create_book",
    "isbn": "9787020158065",
    "fields": {"title_cn": "红楼梦", "author_names": ["曹雪芹"], "publisher_name": "人民文学出版社", "...": "..."},
    "sources": {"title_cn": "douban", "price": "dangdang"},
    "missing": ["catalog"],
    "conflicts": {"publisher_name": {"douban": "人民文学出版社", "dangdang": "人民文学"}},
    "existing": {"book_id": 135, "title_cn": "红楼梦（下）"},
    "updated_at": null
  },
  "candidates": [],
  "message": ""
}
```

- `intent=none` → 前端回退到 RAG 问答；
- 有 `draft` 时按「修改草稿」处理，返回更新后的草稿；
- 编辑意图先返回 `candidates`（引用或搜索命中多本时让用户选）。

### `POST /api/assistant/commit`（登录）

```json
{"draft": {"...": "..."}, "duplicate_choice": "update | add | abandon | null"}
```

- 创建：作者/出版社按名称精确匹配，不存在则创建；品牌/系列/分类只匹配已有项；
- 更新：只写草稿中的字段，`updated_at` 不一致返回 409；
- 重复 ISBN：无 choice 时 409 + 已有书信息；`abandon` 不写；
- 失败整单回滚，草稿保留。

### `POST /api/isbn/lookup`（公开只读）

`{isbn?, title?, author?, publisher?, publish_date?}` → `{fields, sources, missing, conflicts, candidates}`；
手动表单与助手共用。

## 6. 存储

- 元数据缓存：`DATA_DIR/metadata_cache.db`
  ```sql
  metadata_cache(cache_key TEXT PRIMARY KEY, source TEXT, payload TEXT, fetched_at REAL, expires_at REAL)
  ```
- 封面：`DATA_DIR/media/images/books/<sha1>.<ext>`，`thumb_image` = `/images/books/<sha1>.<ext>`，
  媒体路由本地优先、CDN 回退。
- 审计：`DATA_DIR/assistant_actions.jsonl`（user_id、action、book_id、字段摘要、来源、时间）。

## 7. 分期

### P1 — 录入

- [ ] 元数据适配器（豆瓣/当当/外文）+ 缓存 + 限流 + 封面转存
- [ ] `GET /api/isbn/{isbn}` 升级、`POST /api/isbn/lookup`、`actions` / `commit`
- [ ] 草稿卡：字段编辑、对话改稿、冲突/缺失、重复 ISBN 三选一
- [ ] 扫码入口、手动表单兜底
- [ ] 审计、鉴权、错误处理
- [ ] 测试与验收（见下）

### P2 — 编辑

- [ ] 定位（引用 > 搜索 > 多候选）、diff 草稿、`updated_at` 并发校验
- [ ] 用元数据补全缺失字段（diff 确认）
- [ ] 验收：只写变更字段；冲突被拒；补全只填空字段

## 8. 测试与验收

- 后端：豆瓣/当当 HTML fixture（不联网）、合并/权威/缺失/冲突、缓存 TTL、限流/冷却、
  封面下载（mock httpx + Referer）、actions/commit（重复三选一、最小字段、事务回滚、鉴权）、
  `/api/isbn` 兼容、组合检索匹配。
- 前端：草稿卡渲染/编辑、对话改稿、确认/取消、重复三选一、错误态、i18n。
- 回归：后端现有测试、前端现有测试、手动 ISBN 表单。

**P1 验收**：录入 5 本不同情况的书（豆瓣全 / 豆瓣缺价格 / 仅当当有 / 外文 / 查不到）均符合预期；
确认前数据库无新增；重复 ISBN 三选一正确；抓取失败可手填。

## 9. 实现状态

### P1 — 已完成

- 元数据服务：豆瓣页面解析、当当搜索回退（GB18030 查询编码）、外文 ISBN 回退；
  字段权威合并、冲突/缺失报告、SQLite 缓存（30 天）、每源 2.5s 节流与 403/418 冷却；
- 封面：豆瓣带 Referer 下载 → `data/media/images/books/<sha1>.<ext>`，媒体路由本地优先；
- 接口：`GET /api/isbn/{isbn}` 升级；`POST /api/isbn/lookup`（ISBN + 组合检索）；
  `POST /api/assistant/actions`、`POST /api/assistant/commit`（均需登录）；
- 草稿：可编辑、对话改稿、来源/缺失/冲突提示、重复 ISBN 三选一、查不到时手动录入兜底；
- 写入：作者/出版社精确匹配或创建，品牌/系列/分类不自动创建；单次确认后调用现有建书/更新逻辑；
  审计写入 `DATA_DIR/assistant_actions.jsonl`。
- 测试：`tests/test_metadata.py`、`tests/test_assistant_actions.py`、前端 `DraftCard.test.js` 与 Assistant 集成用例。

### P2 — 已完成

- 定位：引用当前书 / 书名搜索（多候选让用户选）/ ISBN；
- 草稿：只列变更字段的 diff、当前值对照、`updated_at` 并发校验、只写变更字段；
- 全字段：书名/作者/出版社/日期/页数/定价/装帧/译者/语言/简介/目录/豆瓣评分/标签，
  以及购入价格/日期/书店、阅读状态、书架/分类/品牌/系列、纸张/装帧形式/册数/版次/印次/印数、
  登记/心愿单/链接；作者与出版社可创建，品牌/系列/分类/书架只匹配已有项；
- 元数据补全：按 ISBN（无 ISBN 时按书名+作者搜索）只填当前为空的字段，diff 确认后写入；
- 重复 ISBN 草稿提供「查看已有书」链接。

### 待补

- 对话框扫码入口（当前支持直接输入 ISBN，扫码组件已有，接线待做）；
- 草稿卡的字段级来源切换（冲突时默认权威源，暂不支持一键切换）；
- 简介/目录等长文本的卡片内编辑（目前通过对话修改，diff 中可见）。

## 10. 风险

| 风险 | 对策 |
|---|---|
| 豆瓣/当当 HTML 改版 | 解析器独立 + fixture 契约测试；失败降级手动 |
| ToS / 封禁 | 个人自用、低频、缓存、来源标注；403 冷却；不批量 |
| 豆瓣封面 418 | 服务端带 Referer 代理下载 |
| LLM 改稿误提取 | 字段白名单 + 类型校验 + 卡片确认 |
| 作者/出版社重复 | 精确匹配；只自动创建作者/出版社；来源可追溯 |
| 并发覆盖 | `updated_at` 校验 |
| 半写入 | 提交失败回滚 |
