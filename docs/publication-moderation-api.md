# 上下架与封禁

## 接口

所有接口沿用 Session；写接口需要真实 CSRF token。作者入口只访问本人作品，管理员身份不能代替作者操作他人作品。所有命令携带当前 version，不接受 ownerId、任意状态或跳过检查字段。

| 接口 | 输入与行为 |
| --- | --- |
| `POST /api/creator/videos/{id}/offline` | `{ "version": 2 }`，本人下架 |
| `POST /api/creator/videos/{id}/publication` | `{ "version": 3 }`，未修改且仍获批准的作品再次发布 |
| `GET /api/admin/videos?page=1&size=20` | ADMIN，未删除且处理状态 READY 的作品列表，包含封禁作品 |
| `GET /api/admin/videos/{id}` | ADMIN，READY 作品管理详情 |
| `POST /api/admin/videos/{id}/block` | `{ "version": 2, "reason": "标题违规" }`，强制下架并封禁 |
| `POST /api/admin/videos/{id}/unblock` | `{ "version": 3, "reason": "允许修改后重审" }`，解除封禁并重置审核 |
| `GET /api/creator/videos/{id}/moderation-history` | 本人查看封禁/解禁原因历史，支持 page/size |
| `GET /api/admin/videos/{id}/moderation-history` | ADMIN 查看 READY 作品的同类历史，支持 page/size |

命令返回 CreatorVideoView，字段与 [作者作品接口](creator-video-api.md) 一致。管理查询每项为 `{ownerId,video}`，ID 为字符串。历史为 `{items,total,page,size}`，每条包含字符串 id、actorId、action、reason、createdAt。历史仅取目标作品的 VIDEO_BLOCKED/VIDEO_UNBLOCKED，不能据此读取其他操作审计或请求标识。时间沿用 UTC 约定。

管理列表按作品 ID 倒序，历史按审计 ID 倒序；页码 1～1000，每页 1～50，默认 20。管理读取明确授予 ADMIN 查看 READY 作品元信息的权限，不开放任意未上传草稿或处理中的作品。媒体预览仍遵循原有三种用途的权限规则，不因管理元信息可读而开放任意私有视频播放。

## 状态规则

| 操作 | 必须满足 | 成功后 |
| --- | --- | --- |
| 作者下架 | 本人、有效账号、完整 READY、APPROVED、PUBLISHED、未封禁 | OFFLINE，清空 publishedAt，保留 APPROVED |
| 作者再次发布 | 本人、有效账号、完整 READY、APPROVED、OFFLINE、未封禁 | PUBLISHED，publishedAt 更新为此次发布时间 |
| 管理员封禁 | 有效 ADMIN、完整 READY、未封禁、未删除 | moderationBlocked=true、OFFLINE、清空 publishedAt，保留原审核结论/轮次 |
| 管理员解禁 | 有效 ADMIN、完整 READY、已封禁、未删除 | 清除封禁、保持 OFFLINE、NOT_SUBMITTED，清空审核提交时间和发布时间，保留历史轮次 |

完整 READY 同时检查当前处理代次 SUCCEEDED、正确归属的源文件、封面及媒体元信息。首版封禁仅支持处理完成的作品，不接管上传/运行中的任务，也不创建取消/恢复任务的半套流程；处理期间治理能力尚未实现。可以封禁待审或未提交但已完成处理的作品，也可以治理已停用作者的 READY 作品；作者停用不限制有效管理员处理。

待审作品封禁后退出可审核队列，旧决策和旧预览参数失效。解禁不恢复原审核批准，不自动发布；作者必须重新提交，新轮次在原 reviewRound 基础上加一。历史审核记录保留。解除封禁的管理员可以与封禁者不同；封禁不沿用“禁止审核自己”的规则，管理员可下架自己的作品，但仍不能审核自己的作品。

作者下架后，如果内容未修改可以直接再次发布；分类停用不会撤销已有审核批准，所以未修改内容再次发布不重新要求分类启用。编辑内容仍使用已有编辑用例，撤销批准并进入 NOT_SUBMITTED，必须重新提交审核。封禁期间不能编辑、审核、发布或签新媒体链接；解禁后恢复本人预览，公开播放仍等待新审核通过。

## 权限、事务与并发

PublicationController → PublicationService → PublicationRepository/MyBatis，领域状态规则位于 CreatorVideo，原因规则位于 ModerationReason。Controller 不访问 Mapper；用途分为显式上下架/封禁/解禁用例，客户端不能任意指定新状态。

写事务先锁有效操作账号并核对角色快照，再锁作品及当前处理任务；检查归属、version 和状态，执行带版本/状态/资源条件的 UPDATE，最后写审计。没有外部存储或 MQ 操作。解禁不写分类引用，无需额外分类锁，不引入“视频后分类”的反向锁序。并发审核仍沿用原先的账号 → 分类 → 视频/任务顺序。

状态、版本和审计（含原因）同事务提交；审计失败全部回滚。事务预算保持 5 秒，没有延长以掩盖竞争。与审核、编辑、重复请求或其他管理员竞争时，同一 version 只允许一次成功；失败方必须刷新，不自动替换为最新版本。客户端遇到未知提交结果应先查询当前状态和历史，不能把重试的 409 当成前一次一定失败。

操作不是请求键幂等 API：旧版本重放返回 STALE_WRITE（409），同状态重复操作返回 PUBLICATION_STATE_CONFLICT（409），不会重复生成原因或审计。资源条件在读取后仍于条件更新复核，条件不符返回 409。每次成功恰好递增一次版本。删除/非本人返回 404，非 ADMIN 调管理用例返回 403，失效账号或旧角色快照返回 401；数据库故障/锁超时返回 503。

封禁/解禁原因去首尾空白后必须非空，最多 500 个 Java 字符，拒绝非法 UTF-8 和除换行/制表外的控制字符；内部用例也拒绝空原因对象。无效原因返回 INVALID_MODERATION_REASON（400）。HTTP 缺失/负版本、超长原因等 Bean Validation 错误返回 400，未知 JSON 字段返回 INVALID_REQUEST（400）。

V9 仅追加 operation_log.reason 可空列和目标类型/目标 ID/审计 ID 索引，不修改已发布迁移；已有审计原因保持 null。旧审计写入接口通过默认委托传 null，封禁用例明确传经过校验的原因。没有新增可篡改/删除历史的接口。管理 READY 查询及 COUNT 的规模成本仍需测量，分页上限不表示已有容量保证。

下架与封禁只阻止后续授权；已签发的媒体链接仍受原期限约束，已经开始的传输不会由数据库状态变更中断，见 [媒体授权限制](public-media-api.md)。没有同步删除对象或发送补偿任务。

## 验证边界

PublicationIntegrationTest 覆盖上下架、封禁后重审、待审轮次失效、越权/角色变化/停用、缺失资源/删除/未处理目标、原因历史与分页、HTTP CSRF/未知字段，以及四条写链路的审计失败回滚。并发包含重复下架、两个管理员封禁，以及各重复三次的发布对封禁、审核对封禁竞争。

首轮 H2 专项中 18 项通过，1 项在既有审核提交准备阶段触发 5 秒事务超时；生产时限保持原值，最终复跑 19 项通过。真实 MySQL 专项 19 项通过。最终真实 MySQL 8.4.3 + RabbitMQ 4.0.5 + FFmpeg 9.0.2 全量 166 项：165 通过、1 项真实 MinIO 跳过、零失败/错误，V9 迁移通过。运行使用 JDK 23 编译到 Java 21；Flyway 的 MySQL 版本支持提示仍存在。真实 MinIO、浏览器、JDK 21 实际运行与容量仍未验收，前端尚未接入。
