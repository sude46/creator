# 帧间：架构设计

> 本文描述当前实现，不把未完成能力写成已交付。项目定位是完整 UGC 原型，不是生产上线平台。

## 1. 总览

帧间采用模块化单体，API 和 Worker 两个进程共享代码与数据库，通过 RabbitMQ 解耦媒体处理。

```text
┌──────────────┐
│   Browser    │
│ Vue 3 + TS   │
└──────┬───────┘
       │ HTTP
┌──────▼───────┐        ┌──────────────┐
│ Nginx / Web  │        │   RabbitMQ   │
└──────┬───────┘        └──────▲───────┘
       │                       │
┌──────▼───────┐   publish     │
│  Spring API  ├───────────────┘
│  - auth      │
│  - upload    │        ┌──────────────┐
│  - business  │        │ Spring Worker│
│  - signing   │        │  - FFmpeg    │
└──┬───┬───┬───┘        │  - HLS       │
   │   │   │            └──┬───┬───┬───┘
   │   │   └───────┐       │   │   │
   │   │       ┌───▼───────▼───▼───▼───┐
   │   │       │      MySQL 8.4        │
   │   │       └───────────────────────┘
   │   │
   │   └──────────────────────┐
   │                          │
┌──▼──────────┐        ┌──────▼─────────┐
│ COS / MinIO │        │ 对象存储私有桶 │
│ 源/封面/HLS │        │ 短期签名访问   │
└─────────────┘        └────────────────┘
```

## 2. 模块划分

```text
com.creator
  ├── identity      账号、登录、Session、设备会话、管理员范围
  ├── catalog       分类
  ├── video         视频、上传、处理、审核、发布、互动、通知、HLS、字幕
  ├── admin         管理概览
  ├── audit         审计
  ├── common        异常、分页、错误映射、通用 Web
  └── bootstrap     启动初始化
```

每个业务包内部：

```text
web             HTTP 参数、DTO、过滤器
application     用例、事务、权限、状态编排、审计
domain          记录、枚举、规则、仓库接口
infrastructure  MyBatis、对象存储、RabbitMQ、FFmpeg
```

关键约束：

- Controller 不直接访问 Mapper 或对象存储 SDK。
- 状态变更由 application 用例管理。
- domain 不依赖 Spring、HTTP、MyBatis。
- 网络、FFmpeg、对象存储调用不放在数据库事务内。

## 3. 运行时

### API

- HTTP、Spring Security、CSRF
- 注册/登录/设备会话
- 草稿、上传、审核、发布、评论、通知、举报、申诉
- 公开查询、媒体签名
- HLS playlist 生成与分片签名
- 管理端接口

### Worker

- 消费 RabbitMQ 媒体任务
- FFprobe 校验
- FFmpeg 抽封面
- FFmpeg 生成最多 3 档 HLS
- 上传封面、playlist、TS
- 写入媒体结果和 HLS 记录
- HLS 历史回填任务轮询

### Web

- Nginx 提供 Vue 静态资源
- `/api` 反向代理到 Spring API
- 生产需要 HTTPS 和同源 Cookie

## 4. 数据模型

主要表：

| 领域 | 表 |
| --- | --- |
| 账号 | `user_account`、`user_profile`、`user_session`、`user_follow` |
| 分类 | `video_category` |
| 视频 | `video`、`media_asset`、`upload_attempt`、`upload_chunk` |
| 处理 | `processing_task`、`processing_output` |
| 审核发布 | `review_record`、`video_deletion` |
| 互动 | `video_like`、`video_favorite`、`video_comment`、`video_comment_like` |
| 收藏/历史 | `favorite_folder`、`favorite_folder_item`、`view_history` |
| 合集 | `video_collection`、`video_collection_item` |
| 标签 | `video_tag`、`video_tag_item` |
| 通知 | `notification` |
| 治理 | `content_report`、`content_appeal`、`operation_log` |
| 媒体扩展 | `video_hls`、`video_hls_segment`、`video_subtitle`、`hls_backfill_task` |

视频状态：

```text
processing_status: PENDING | PROCESSING | READY | FAILED
review_status:     NOT_SUBMITTED | PENDING | APPROVED | REJECTED
publication_status: UNPUBLISHED | PUBLISHED | OFFLINE
```

公开条件同时要求：作者启用、未删除/封禁、READY、源文件和封面存在、当前处理代次成功、审核 APPROVED、发布 PUBLISHED、发布时间存在。

Flyway V1～V36 管理结构演进；每个模块新增表/字段都有迁移。

## 5. 核心链路

### 5.1 登录与设备会话

```text
登录
→ Spring Security DaoAuthenticationProvider
→ Session fixation 保护
→ 注册 user_session（SHA-256(JSESSIONID)、IP、UA、版本）
→ 后续请求 ActiveAccountFilter 校验账号/角色/设备行/哈希/过期/版本
```

改密/管理员重置：

```text
更新密码
→ session_version + 1
→ 撤销目标账号全部设备会话
→ 旧 Session 下一次请求 401
```

设备会话数据库只存 Session ID 哈希，不存原始 JSESSIONID。

### 5.2 草稿与上传

```text
创建草稿（requestKey 幂等）
→ 创建分片会话（大小、8 MiB 分片、文件指纹）
→ 逐片上传（幂等、长度校验、磁盘/并发准入）
→ 完成组装 + MP4 头校验
→ storage.put
→ 事务写入 media_asset、source_asset_id、processing_task
```

失败补偿：

- 过期上传尝试和临时分片由维护任务清理
- 重复上传同分片幂等
- 对象写入成功但绑定失败：保留尝试，过期回收
- 响应丢失：客户端查询 `sourceUploaded`，不重复绑定

### 5.3 媒体处理

```text
processing_task PENDING
→ RabbitMQ
→ Worker claim（执行令牌 + 租约）
→ 下载源文件
→ FFprobe 校验
→ FFmpeg 抽封面
→ FFmpeg 生成 HLS
→ 上传封面/HLS
→ 成功事务：READY + 媒体资产 + HLS 记录
```

- 任务失败进入 RETRY_WAIT，有界退避
- 租约过期可恢复
- 上传一半的 HLS 尽力清理
- 封面有 processing_output 清理记录

### 5.4 HLS

- 按源高度生成最多 3 档，避免放大
- 4 秒分片
- 私有对象存储
- 公开 master/media playlist
- media playlist 中相对 `.ts` 改写为短期签名 URL
- 前端 hls.js；fatal 错误回退 MP4
- 历史作品由超级管理员触发 HLS-only 回填

### 5.5 审核与发布

```text
送审（version/reviewRound）
→ 管理员审核（条件更新）
→ APPROVED / REJECTED
→ 作者上下架
→ 管理员封禁/解禁
```

版本号 + 条件更新避免并发覆盖；封禁/解禁/删除记录原因和审计。

### 5.6 评论与通知

```text
评论/回复
→ 写入评论
→ 通知作品作者/被回复人
→ 解析 @用户名（最多 5 个）
→ 跳过自己/已通知/重复提及
→ 写 MENTION 通知
```

通知支持：

- 类型筛选
- 单条已读
- 删除、清空已读
- 同作品未读评论/回复聚合

## 6. 关键取舍

### 模块化单体

当前规模不需要服务发现、分布式事务和独立发布；API/Worker 已分进程。拆分需要明确瓶颈和团队边界。

### 版本号 + 条件更新

编辑、送审、审核、发布使用 `version`，冲突返回 409；不依赖前端按钮禁用。

### 对象存储与数据库分离

对象存储写入不在数据库事务内；失败靠尝试记录、过期清理、幂等和条件更新补偿。

### 私有桶 + 短期签名

源文件、封面、HLS 均在私有桶；客户端不能指定 key/桶/有效期。已签发链接在 TTL 内不可撤销。

### 同步通知

当前评论/关注/审核在业务事务内写通知，简单但耦合；生产可演进 outbox/异步事件。

### 搜索

当前多字段 LIKE，不是全文检索引擎；没有分词、相关度、纠错、热门搜索。

### 字幕/头像 BLOB

部署简单、事务一致，但数据库膨胀；上量应迁对象存储。

### 单实例 Session

设备注册表统一撤销，但 HttpSession 仍在单 API 内存；多实例需粘性会话或 Spring Session。

## 7. 测试与验证

- H2：大部分集成测试，真实 Spring Security、Flyway、MyBatis、Controller
- 真实依赖：MySQL、RabbitMQ、FFmpeg、MinIO、COS
- 浏览器：Firefox 完整链路；Chromium 冒烟

当前基线：

```text
H2：255 项，0 失败/错误，12 项真实依赖跳过
真实依赖：255 项，0 跳过，0 失败
```

## 8. 边界

- 不是生产上线平台
- 无 DNS/ICP/TLS/COS CDN/备份/监控/容量验收
- 单 API 实例
- 无 2FA/忘记密码/邮箱验证/账号注销
- 搜索 LIKE，不是全文
- HLS 回填需手动触发
- 无 DRM/视频编辑/AI 生成/移动 App
- 通知同步写
- 字幕/头像 BLOB
