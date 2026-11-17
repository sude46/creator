# 作者作品与处理重试

| API | 行为 |
| --- | --- |
| GET /api/creator/videos | 本人作品分页，page=1、size=20，上限沿用 PageRequest；按 ID 倒序 |
| GET /api/creator/videos/{id} | 本人作品详情，包含上传、处理、审核、发布状态和当前处理代次 |
| POST /api/creator/videos/{id}/processing-retry | JSON `{ "version": 3 }`，需要登录和 CSRF；成功返回最新作品详情 |

作者查询不局限于未上传草稿，能查看处理中的作品、失败原因类别、执行次数、下次执行时间、时长/分辨率及 retryAllowed。不返回对象键、执行令牌、原始异常或存储地址。作品/分类 ID 为字符串，删除的作品不返回；封禁作品仅向本人显示状态，不能重试。管理员身份不赋予访问其他作者私有作品的权限。

手动重试只接受本人、未删除/封禁、源文件已绑定、当前处理任务为 FAILED 且 failureCode 可重试、未提交审核且未发布的作品。不支持用重试修复永久坏文件或不支持的编码。前端 retryAllowed 仅供展示，后端重新校验，并且条件更新不能存在其他活跃任务。

事务先锁有效账号（包含角色快照），再锁作品及当前任务；与上传绑定共用账号锁和 `creator.upload.max-pending` 配额，避免不同作品同时上传/重试突破限制。版本冲突返回 STALE_WRITE（409）；不符合重试条件返回 PROCESSING_RETRY_NOT_ALLOWED（409）；配额已满返回 UPLOAD_QUOTA_EXCEEDED（409）。非本人和不存在均返回 404，账号禁用或角色变化拒绝内部用例调用。

成功事务将 generation 加一、重置处理元信息和状态为 PENDING、新建对应代次任务、记录 PROCESSING_RETRIED 审计；源文件保持原绑定，不复制文件。旧任务及失败记录保留，旧代次消息和执行结果不能作用于新代次。审计失败时版本、代次和任务一并回滚。重复提交同一旧版本返回冲突，不重复开代次。

接口不直接发送 MQ，不依赖上传功能开关。新任务通过既有数据库派发器进入 RabbitMQ，再由 Worker 处理；关闭派发器或 Worker 时会保持可追踪的 PENDING。

原 `/api/creator/drafts` 继续服务创建和未上传草稿编辑；新接口负责全生命周期状态查询，避免上传后在创作中心失去作品。编辑已处理作品和审核接口见 [审核接口](video-review-api.md)。详情同时返回 reviewRound、reviewSubmittedAt、publishedAt，审核命令必须使用当前 version。
