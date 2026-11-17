# 审核与发布状态

当前后端完成作者提交/撤回、管理员通过/驳回、驳回后编辑并重新提交。通过审核同事务设置 PUBLISHED；这里是数据库发布状态，公开页面、播放签名、审核媒体预览及管理员前端仍需后续模块接入。

## 作者接口

| API | 请求与行为 |
| --- | --- |
| PUT /api/creator/videos/{id} | `{ "version": 3, "title": "标题", "description": "简介", "categoryId": "1" }`；只编辑已处理 READY 且非待审作品 |
| POST /api/creator/videos/{id}/review-submissions | `{ "version": 3 }`；创建新审核轮次，变为 PENDING |
| POST /api/creator/videos/{id}/review-withdrawal | `{ "version": 4 }`；只撤回当前 PENDING 轮次，变为 NOT_SUBMITTED |
| GET /api/creator/videos/{id}/reviews | 本人审核历史，page/size 有界，按轮次倒序 |

所有写请求需要 Session、CSRF，身份从服务端 Actor 获取。只能操作本人作品，管理员不能借作者接口操作别人的作品。未上传草稿仍使用 `/api/creator/drafts` 编辑；处理中的作品暂不开放元信息编辑。

提交要求源文件已绑定、当前代次任务 SUCCEEDED、状态 READY、时长尺寸存在且封面已绑定，作品未删除/封禁，账号和分类有效。未审核或 REJECTED 才可提交；每次提交增加 reviewRound 和 version。审核中禁止编辑。撤回同样要求有效版本，原轮次不复用，下次提交继续加轮次。

已发布作品编辑后，原结论失效：同时设置 NOT_SUBMITTED、UNPUBLISHED，清除 publishedAt。首版没有“旧版本继续公开、新版本等待审核”的版本库；修改后必须再次提交，不能沿用已通过结论。编辑沿用停用分类时允许保存已有元信息，但提交与通过审核必须选择有效分类。驳回后允许不编辑直接重新提交；旧原因仍在历史中。

## 管理员接口

| API | 请求与行为 |
| --- | --- |
| GET /api/admin/reviews | 待审队列，page/size 有界，按提交时间和作品 ID 稳定排序，排除自己的作品 |
| GET /api/admin/reviews/{id} | 当前可审核详情，返回 version、reviewRound 和作品元信息 |
| POST /api/admin/reviews/{id}/decision | `{ "version": 4, "reviewRound": 2, "outcome": "REJECTED", "reason": "请修改标题" }` |
| GET /api/admin/reviews/{id}/history | 有审核提交或历史记录的未删除作品历史；不开放任意私有草稿读取 |

决策 outcome 只接受 APPROVED/REJECTED，驳回原因去首尾空白后必填，最多 500 位，拒绝非法控制字符。通过可附原因，也可为空。ADMIN 鉴权同时在过滤链与用例执行，不允许审自己的作品；写入前重新验证管理员账号有效性和角色快照。

决策必须传管理员实际看到的 version 和 reviewRound。拒绝代替旧请求自动读取当前版本：作者撤回、重新提交或其他管理员已处理后，旧决策不能写入。状态/轮次冲突返回 REVIEW_STATE_CONFLICT（409），旧版本 STALE_WRITE（409）；不存在或不可读取的目标返回 404。两管理员竞争只能留下一次决定和一份审核记录。

通过同事务设置 APPROVED、PUBLISHED、publishedAt；驳回设置 REJECTED、UNPUBLISHED，publishedAt 为空。更新成功后写 review_record 及 operation_log；记录或审计失败，整个状态更新回滚。原始媒体、存储和签名操作不在审核事务中。

## 历史与锁

review_record 对 `(video_id, review_round)` 建唯一约束，保留决定时的标题、简介、分类、processingGeneration、contentVersion、管理员、结果、原因和时间，后续编辑不会覆盖历史。历史只读，ID 用字符串，分页遵守 PageRequest 的页数和大小上限，不返回源对象键或执行令牌。

作者和管理员写入首先锁自己的有效账号，需要验证分类时再锁分类，最后锁作品及当前任务；所有作品条件写入再校验版本、状态及媒体资源。通过和驳回都提前锁分类：驳回允许分类停用，但写审核历史的分类外键也会申请锁，不能等持有作品锁后才申请。真库并发测试发现的这条反向锁序已修正。账号锁不是全局串行锁，不同账号操作不同作品可并行。

V7 为追加迁移，不修改 V1～V6。review_record 使用外键保留稳定关联，不开放删除历史接口；自动化测试先清理 review_record 再清理作品/账号，专用测试库不得包含业务数据。

## 验证范围

新增集成测试覆盖通过发布、驳回重提及历史快照、撤回与版本隔离、发布后编辑撤下、普通用户/本人/管理员边界、CSRF、驳回原因、分类停用、封禁/删除/停用账号、双管理员竞争、撤回与决策竞争，以及所有写路径的审计失败回滚。尚不把审核状态测试当作浏览器看完视频后审核或公开播放验收。

2026-11-17 最终真 MySQL + RabbitMQ + FFmpeg 全量 132 项通过，零失败、错误和跳过；其中审核专项 19 项包含五次双管理员竞争。V7 追加迁移已在 MySQL 8.4.3 上执行。运行仍使用 JDK 23 编译到 Java 21，真实 JDK 21 与服务器部署边界仍需后续验证。
