# BookCollection API

Base URL: `/api/book-collections`

## Data Models

### BookCollectionCreation
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | ✓ | 书单名称 |
| `intro` | string \| null | | 书单简介 |

### BookCollectionUpdate
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string \| null | | 书单名称 |
| `intro` | string \| null | | 书单简介 |

### BookCollectionResponse
| Field | Type | Description |
|-------|------|-------------|
| `id` | int | 书单 ID |
| `name` | string | 书单名称 |
| `intro` | string \| null | 书单简介 |
| `created_at` | datetime \| null | 创建时间 |
| `total_books` | int \| null | 图书数量（从 books 数组自动计算） |
| `books` | BookSimple[] \| null | 书单中的图书列表 |

### BookSimple
| Field | Type | Description |
|-------|------|-------------|
| `id` | int | 图书 ID |
| `title` | string | 书名 |
| `title_cn` | string \| null | 中文书名 |
| `thumb_image` | string \| null | 封面图片路径 |
| `isbn` | string \| null | ISBN |
| `authors` | string[] \| null | 作者列表 |

### AddBookToCollection
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `book_id` | int | ✓ | 要添加的图书 ID |

### BatchAddBooks
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `book_ids` | int[] | ✓ | 要批量添加的图书 ID 列表 |

---

## Endpoints

### 1. 创建书单

```
POST /api/book-collections/
```

**Request Body**: `BookCollectionCreation`

```json
{
  "name": "推理小说精选",
  "intro": "最爱推理"
}
```

**Response** `200`: `BookCollectionResponse`

```json
{
  "id": 1,
  "name": "推理小说精选",
  "intro": "最爱推理",
  "created_at": "2026-06-13T01:09:07.434356",
  "total_books": 0,
  "books": []
}
```

---

### 2. 获取书单列表

```
GET /api/book-collections/?page=1&limit=10&sort_by=name
```

**Query Parameters**:

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 1 | 页码 |
| `limit` | int | 10 | 每页数量 |
| `sort_by` | string | `name` | 排序: `name` \| `created_at` |

**Response** `200`:

```json
{
  "book_collections": [
    {
      "id": 1,
      "name": "推理小说精选",
      "intro": "最爱推理",
      "created_at": "2026-06-13T01:09:07",
      "total_books": 3
    }
  ],
  "total_pages": 1,
  "total_collections": 1
}
```

---

### 3. 获取书单详情

```
GET /api/book-collections/{collection_id}
```

**Response** `200`: `BookCollectionResponse`

```json
{
  "id": 1,
  "name": "推理小说精选",
  "intro": "最爱推理",
  "created_at": "2026-06-13T01:09:07",
  "total_books": 2,
  "books": [
    {
      "id": 1063,
      "title": "100个成语中的古代生活史",
      "title_cn": "100个成语中的古代生活史",
      "thumb_image": "books/9787559825407.png",
      "isbn": "9787559825407",
      "authors": ["[当代] 许晖"]
    }
  ]
}
```

**Errors**:
| Code | Detail |
|------|--------|
| 404 | Book collection not found |

---

### 4. 更新书单

```
PUT /api/book-collections/{collection_id}
```

**Request Body**: `BookCollectionUpdate`（所有字段可选，只提交要更新的字段）

```json
{
  "intro": "更新后的简介"
}
```

**Response** `200`: `BookCollectionResponse`

---

### 5. 删除书单

```
DELETE /api/book-collections/{collection_id}
```

**Response** `200`:

```json
{
  "message": "Book collection deleted"
}
```

---

### 6. 添加单本图书

```
POST /api/book-collections/{collection_id}/books
```

**Request Body**: `AddBookToCollection`

```json
{
  "book_id": 1063
}
```

**Response** `200`: `BookCollectionResponse`（包含更新后的 books 列表）

**Errors**:
| Code | Detail |
|------|--------|
| 404 | Book collection not found |
| 404 | Book not found |
| 400 | Book already in collection |

---

### 7. 批量添加图书

```
POST /api/book-collections/{collection_id}/books/batch
```

**Request Body**: `BatchAddBooks`

```json
{
  "book_ids": [1062, 1063, 1064]
}
```

**Response** `200`: `BookCollectionResponse`

**Errors**:
| Code | Detail |
|------|--------|
| 404 | Book collection not found |
| 404 | Books not found: [id1, id2] |
| 400 | Books already in collection: [id1] |

---

### 8. 从书单移除图书

```
DELETE /api/book-collections/{collection_id}/books/{book_id}
```

**Response** `200`: `BookCollectionResponse`

**Errors**:
| Code | Detail |
|------|--------|
| 404 | Book collection not found |
| 404 | Book not found |
| 400 | Book not in collection |

---

## 辅助端点

### 获取全部图书标题（用于下拉选择）

```
GET /api/books/titles
```

返回所有 `in_wish=false` 的图书，格式 `[{id, name}]`，按书名字母排序。已排除愿望单图书。

```json
[
  {"id": 1063, "name": "100个成语中的古代生活史"},
  {"id": 1062, "name": "100个日常俗语中的古代社会史"}
]
```
