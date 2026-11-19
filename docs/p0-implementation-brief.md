# 帧间 P0 实现契约

本文是 B1、B2、B3、F3、F4、F5 的冻结约定。后续实现以本文件为准，不在代码里各自解释。

## 0. 术语与品牌

- 产品名只叫“帧间”。
- 内容单位叫“作品”；“视频”只指媒质文件/播放器。
- 产品名后不追加任何定位后缀。

## 1. B1：锚点深链

### 请求

```http
GET /api/videos?anchorId=5&before=3&after=4
```

- `before` 默认 3，范围 0～20。
- `after` 默认 4，范围 0～20。
- `before + 1 + after` 不得超过 50。
- `title`、`categoryId` 等原有筛选先生效，再在筛选结果内定位锚点。
- 排序固定为 `published_at DESC, id DESC`。

### 响应

```json
{
  "items": [],
  "anchorIndex": 3,
  "hasBefore": true,
  "hasAfter": true
}
```

- `anchorIndex`：锚点在 **items 窗口内**的 0-based 下标，不要求全局位置。
- `items`：最多 `before + 1 + after` 条，锚点位于 `anchorIndex`。
- `hasBefore`：窗口之外是否还有较新的公开作品。
- `hasAfter`：窗口之外是否还有较旧的公开作品。
- 不返回 `total`，不额外做总数 count。
- 不使用 `size`；`size` 只属于普通分页列表。

### 边界

- `anchorId` 不存在、已删除、未发布、被封禁、或不在筛选结果内：
  - 仍返回锚点响应结构；
  - 返回最新窗口 `before + 1 + after` 条；
  - `anchorIndex = null`；
  - `hasBefore = false`；
  - `hasAfter` 按窗口之后是否还有更旧作品计算。
- 锚点位于列表开头：`anchorIndex = 0`，`hasBefore = false`。
- 锚点位于列表末尾：`anchorIndex = 实际前序数量`，`hasAfter = false`。
- 普通列表 `GET /api/videos?page=1&size=12` 保持现有 `items/total/page/size`，不受 B1 影响。
- 锚点查询最多额外读取 `before + 1` 和 `after + 1` 条，用于判断 `hasBefore/hasAfter`，不做 total。

### 验收

- `anchorId=5&before=3&after=4` 返回 8 条以内，`items[anchorIndex].id == 5`。
- 不存在的 id 返回最新窗口，`anchorIndex=null`，`hasBefore=false`。
- 分类筛选后的锚点仍满足上述规则。
- 普通列表响应字段不变。

## 2. B2：封面懒加载与签名 TTL

### 列表字段

公开列表、作者列表、管理列表补充：

```json
{
  "hasCover": true,
  "coverKey": "covers/....jpg"
}
```

- `coverKey` 只作为不透明缓存键和排查信息，不参与前端鉴权。
- 不返回 `coverUrl`，不在列表阶段批量生成短期签名。
- 公开列表本身只包含带封面的可公开作品；`hasCover` 仍显式返回，避免前端猜测。

### 签名 TTL

- 播放/源文件签名：保持 120 秒。
- 封面签名：新增 `MEDIA_COVER_LINK_SECONDS`，默认 300 秒。
- `SignedMedia.expiresAt` 必须反映实际 TTL。

### 前端懒加载

- 卡片进入视口后才请求 `POST /api/videos/{id}/media?kind=COVER`。
- 使用 `IntersectionObserver`，离开视口不主动取消已缓存 URL。
- 封面签名并发上限 4；超出的请求排队，不一次性打满。
- 内存缓存 `{ videoId, coverKey, url, expiresAt }`。
- 在 `expiresAt - 30s` 时重签；卡片仍在视口内时继续展示旧图直到新 URL 到达。
- 页面刷新后允许清空缓存；不写入 localStorage。

### 验收

- 列表闲置 5 分钟后继续滚动，新进入视口的封面仍能加载。
- 同时只发起最多 4 个封面签名请求。
- 同一作品在缓存有效期内不重复签名。
- 播放 TTL 仍为 120 秒，封面 TTL 为 300 秒。

## 3. B3：统一状态契约

### 枚举

```text
NEEDS_UPLOAD
PROCESSING
FAILED
READY_TO_SUBMIT
PENDING_REVIEW
REJECTED
PUBLISHED
TAKEN_DOWN
BLOCKED
```

### 派生优先级

后端根据现有字段派生，不新增状态表，不改现有数据库字段：

1. `moderation_blocked = TRUE` → `BLOCKED`
2. 源文件未绑定 → `NEEDS_UPLOAD`
3. `processing_status IN (PENDING, PROCESSING)` → `PROCESSING`
4. `processing_status = FAILED` → `FAILED`
5. `review_status = PENDING` → `PENDING_REVIEW`
6. `review_status = REJECTED` → `REJECTED`
7. `review_status = APPROVED AND publication_status = PUBLISHED` → `PUBLISHED`
8. `publication_status = OFFLINE` → `TAKEN_DOWN`
9. 其余 READY 且未送审 → `READY_TO_SUBMIT`

`TAKEN_DOWN` 只表示作者下架；`BLOCKED` 只表示管理员封禁。两者不能合并。解禁后仍走重新送审，不直接发布。

### 作者端文案与主操作

| 状态 | 作者端文案 | 主操作 |
| --- | --- | --- |
| NEEDS_UPLOAD | 待上传视频 | 上传视频 |
| PROCESSING | 处理中，预计 1～2 分钟 | 无，自动刷新 |
| FAILED | 处理失败：{人话原因} | 重新处理 |
| READY_TO_SUBMIT | 待提交审核 | 提交审核 |
| PENDING_REVIEW | 待审核 | 撤回 |
| REJECTED | 未通过：{最近一次驳回原因} | 修改后重新提交 |
| PUBLISHED | 已发布 | 播放我的作品 |
| TAKEN_DOWN | 已下架 | 无 |
| BLOCKED | 已封禁 | 查看处理原因 |

### 管理端文案

| 状态 | 管理端文案 |
| --- | --- |
| NEEDS_UPLOAD | 待上传 |
| PROCESSING | 处理中 |
| FAILED | 处理失败 |
| READY_TO_SUBMIT | 未送审 |
| PENDING_REVIEW | 待审核 |
| REJECTED | 已驳回 |
| PUBLISHED | 已发布 |
| TAKEN_DOWN | 已下架 |
| BLOCKED | 已封禁 |

### 原因字段

- 响应增加 `status` 和 `statusReason`。
- `REJECTED` 的 `statusReason` 来自最近一次 `REJECTED` 审核记录的 `reason`，为空时用“未通过审核”。
- `FAILED` 的 `statusReason` 由后端映射成人话，例如：
  - `INVALID_MEDIA` → 文件损坏或媒体信息无效
  - `UNSUPPORTED_MEDIA` → 编码、时长或分辨率不符合支持范围
  - `STORAGE_UNAVAILABLE` → 存储暂时不可用
  - `PROCESS_TIMEOUT` → 视频处理超时
- 界面不得直接展示 `TEMP_UNAVAILABLE`、`LEASE_EXPIRED` 等内部枚举名。
- 原始 `failureCode` 可保留给管理端诊断，但作者主文案只使用 `statusReason`。
- `TAKEN_DOWN` 不携带封禁原因；`BLOCKED` 可展示管理员处理原因。

### 验收

- 作者详情、作者列表、管理列表、审核详情返回同一 `status`。
- 被封禁作品显示 `BLOCKED`，作者下架显示 `TAKEN_DOWN`。
- 驳回作品显示最近一次驳回原因。
- 失败作品不出现内部枚举词。
- 状态映射只保留一份后端实现和一份前端展示表，不在各页面重复写判断。

## 4. F3：首页重排

- 去掉独立大幅 hero，标题收敛。
- 搜索框和分类 chips 同一行，窄屏换行。
- 网格：
  - ≥1280px：4 列
  - 1024～1279px：3 列
  - 600～1023px：2 列
  - <600px：1 列
- 卡片：16:9 封面、时长角标、标题两行截断、作者/分类、hover 阴影。
- 同行卡片等高。
- F3 先使用现有封面懒加载方式，不等待 B2；B2 只替换签名策略和 TTL。

## 5. F4：作者详情清理

- 不展示数据库 ID、版本号、审核轮次、画面尺寸。
- 只保留一句状态和一句状态说明。
- 一个主操作按钮；编辑、下载、删除等次要操作收进次级区域或“更多”。
- 处理中/待审核时自动轮询；页面隐藏时暂停。
- 状态来自 B3 的 `status`，不在页面重复组合三个原始状态。

## 6. F5：竖屏 feed

前置条件：B1 完成。

- 路由：`/feed`。
- 卡片进入 feed 时使用 `anchorId` 定位。
- `scroll-snap-type: y mandatory`，使用 `100dvh`。
- `IntersectionObserver` 阈值 0.6，只播放当前条。
- 默认静音自动播放，提供取消静音；状态存 localStorage。
- 只保留前后各 1 个 `<video>`，其余使用封面。
- 横屏视频使用模糊封面背景 + `object-fit: contain`。
- 右侧只放收藏/分享；没有关注、点赞、评论、原声、播放数。
- 顶栏只放“全部 + 启用分类”，不放“推荐”或“关注”假入口。
- 返回网格前记录当前视频 id；返回 feed 时用 `anchorId` 恢复位置。
- 媒体直连 COS 时，CORS 必须允许实际应用来源和 Range 相关响应头。

## 7. 实施顺序

```text
F3 首页重排
B2 封面策略定稿 + 前端懒加载
B3 状态契约 + F4 详情页
B1 anchorId
F5 feed
```

每一步先构建/测试，再独立提交并推送。

## 8. P0 验收清单

- [ ] 品牌只出现“帧间”；标题、meta、OG、页脚、登录页一致。
- [ ] `--green` 修改后主按钮和 Element Plus 主色变体跟随。
- [ ] 作品卡片同行等高，标题两行截断。
- [ ] 首页 1440/1024/375 分别 4/3/1 列。
- [ ] 作者详情不出现 ID、版本号、审核轮次、画面尺寸。
- [ ] 作者详情只有一个主操作，处理中自动轮询。
- [ ] 列表返回 `hasCover`/`coverKey`，不返回 `coverUrl`。
- [ ] 封面 TTL 300 秒，播放 TTL 120 秒，闲置 5 分钟后滚动仍可加载。
- [ ] `anchorId` 返回 `items/anchorIndex/hasBefore/hasAfter`，不返回 `total`。
- [ ] 不存在的锚点回退最新窗口，`anchorIndex=null`，`hasBefore=false`。
- [ ] 9 个状态前后端一致，驳回原因/失败人话原因正确。
- [ ] `TAKEN_DOWN` 与 `BLOCKED` 不合并。
- [ ] `/feed` 深链定位、前后各 1 个 video、静音自动播、返回恢复位置。
- [ ] 空列表、网络错误、404 均有兜底。
