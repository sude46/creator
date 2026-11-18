# 上传与对象存储接入

## 当前范围

后端支持单文件源视频上传、私有对象写入、事务绑定和失败补偿。只接收 MP4 请求体并检查 ftyp 主品牌 isom、iso2、mp41、mp42、avc1；这不是完整媒体校验。编码白名单、音轨、时长、分辨率和封面生成已由独立 [Worker](processing-worker-setup.md) 接入；[公开查询与媒体授权](public-media-api.md) 另有权限和发布状态检查，不能绕过审核直接播放源对象。

上传事务创建真实的 PENDING processing_task，可靠 RabbitMQ 调度器和消费 Worker 已接入，均需按配置启用。投递成功不表示处理成功。已绑定源文件的作品退出草稿入口，通过 [作者作品接口](creator-video-api.md) 查询状态和有界手动重试；READY 作品通过 [审核接口](video-review-api.md) 编辑、提交和重审。

## 存储准备

Java SDK 固定为 io.minio:minio:8.5.17，其发布 POM 声明 Apache License 2.0。2026-11-18 已在本地运行测试候选服务端 RELEASE.2025-04-22T22-12-26Z，核对源码 LICENSE 为 GNU AGPL v3。SDK 许可不代表服务端许可；正式服务端版本、镜像来源、许可适用与维护状态须在部署前确认，当前未在 Compose 添加 MinIO 服务。

先在独立的 MinIO 服务建立 `creator-private` 桶及专用应用账号。该桶要求无匿名 bucket policy、版本化状态为 OFF；不能使用曾启用后仅 Suspended 的桶。应用启动会读取并验证这些设置，无权限读取或设置不符时拒绝启动。不要用 root 管理凭据作为应用凭据。

未完成分片的回收方式由 `STORAGE_INCOMPLETE_UPLOAD_CLEANUP` 显式选择：

- `MINIO_DEFAULT`（默认，目标为 MinIO）：依赖 MinIO 服务端 `api stale_uploads_expiry` 和 `stale_uploads_cleanup_interval` 自动回收。应用凭据通常无权读取服务端 admin 配置，因此启动时无法核验；运维必须执行 `mc admin config get <alias> api`，确认 `stale_uploads_expiry` 不大于 24h（当前测试镜像默认 `24h`，扫描间隔 `6h`）。该镜像不接受仅含 AbortIncompleteMultipartUpload 的生命周期规则（返回 400），且会在混合规则中丢弃 abort 字段，因此**不要对 MinIO 配置该 JSON**。
- `BUCKET_LIFECYCLE`（目标为支持该规则的 S3 兼容存储）：应用读取并强制校验一天期 `AbortIncompleteMultipartUpload` 规则，缺失则拒绝启动。示例：

```json
{
  "Rules": [{
    "ID": "abort-incomplete-sources",
    "Status": "Enabled",
    "Filter": { "Prefix": "sources/" },
    "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 1 }
  }]
}
```

MinIO 默认模式下，应用需要对配置桶读取位置、策略和版本化的权限，以及写入/删除 `sources/*` 和完成/中止分片上传的权限；只有 `BUCKET_LIFECYCLE` 模式还需要读取生命周期配置。运行时不修改桶策略或生命周期；由运维配置并保持。当前适配针对上述 MinIO 私有桶约束，未验证其他 S3 服务的 ACL/公共访问语义。

在启动 Java 的 PowerShell 中设置，值均为示例，实际凭据不入 Git：

```powershell
$env:UPLOAD_ENABLED = 'true'
$env:STORAGE_ENDPOINT = 'http://127.0.0.1:9000'
$env:STORAGE_BUCKET = 'creator-private'
$env:STORAGE_ACCESS_KEY = '替换为应用账号'
$env:STORAGE_SECRET_KEY = '替换为应用密钥'
$env:STORAGE_INCOMPLETE_UPLOAD_CLEANUP = 'MINIO_DEFAULT'
$env:UPLOAD_TEMP_DIRECTORY = 'D:/creator/.local/upload-temp'
```

随后按 README 的数据库配置启动后端。`.env.example` 仅是配置参考，Compose 的环境文件不会自动注入本地 Java 进程。生产存储连接使用可信 TLS 或经过验证的私有网络，桶名及对象存储端点变更需要先迁移现存对象与清理记录。

## 接口

| 请求 | 输入与行为 |
| --- | --- |
| `PUT /api/creator/videos/{id}/source` | Cookie Session、`Content-Type: video/mp4`、真实 CSRF header、`X-Video-Version`；body 为文件二进制 |
| `GET /api/creator/videos/{id}/upload` | 当前登录账号查询自己的作品上传状态 |

响应示例：`{"videoId":"123","version":1,"sourceUploaded":true,"processingStatus":"PENDING"}`。不接收客户端对象键、ownerId、路径、文件名或任务状态，不返回桶名或 URL。200 表示源文件已绑定并留下待处理任务，不能解释为已通过媒体检测或审核。

浏览器在创建草稿后先取得 `/api/auth/csrf` 的 headerName/token，再以 File 作为 body，明确设置 video/mp4 和当前版本，不能发送 FormData。上传响应丢失或 503 时先 GET 查询状态；sourceUploaded=true 时不要重传，后端重复上传返回 SOURCE_ALREADY_BOUND（409）。只有确认未绑定且仍可上传时再用最新版本发起新上传，不做无界自动重试。

缺身份/账号失效返回 401，缺少或错误 CSRF 返回 403，他人作品返回 404，过期版本/配额返回 409，并发或未清理凭据过多返回 429，文件过大 413，格式错误 400，不支持的请求类型 415，读取截止时间 408，存储/数据库/磁盘故障 503。客户端断流可能已无法收到响应；应以状态查询为准。

## 预算与部署要求

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `UPLOAD_ENABLED` | false | 显式开启上传及清理作业 |
| `UPLOAD_MAX_BYTES` | 209715200 | 单文件 200 MiB，上限不能配置得更大 |
| `UPLOAD_MAX_CONCURRENT` | 2 | 实例并发 1～4，每账号固定 1，不排队 |
| `UPLOAD_TEMP_DIRECTORY` | ../.local/upload-temp | 相对后端工作目录；生产使用专用受限目录 |
| `UPLOAD_MIN_FREE_BYTES` | 1073741824 | 临时文件系统保留 1 GiB，并额外预留在途上传最大体积 |
| `UPLOAD_RECEIVE_SECONDS` | 120 | 接收循环截止预算，范围 1～300 秒 |
| `UPLOAD_USER_QUOTA_BYTES` | 1073741824 | 已绑定源文件的逻辑配额，默认每账号 1 GiB |
| `UPLOAD_MAX_PENDING` | 5 | 每账号待处理源文件及未回收上传凭据各自的数量上限，范围 1～20 |
| `UPLOAD_ATTEMPT_SECONDS` | 900 | 上传凭据宽限期；须至少为接收预算 + 240 秒 |
| `STORAGE_OPERATION_SECONDS` | 60 | 单次存储操作的整体 HTTP 预算，范围 5～120 秒 |
| `STORAGE_INCOMPLETE_UPLOAD_CLEANUP` | MINIO_DEFAULT | `MINIO_DEFAULT` 依赖 MinIO 服务端过期分片清理；`BUCKET_LIFECYCLE` 强制校验 S3 生命周期规则 |

同账号的最终配额计算在账号行锁内完成，上传预检查之后仍在绑定事务重新检查。未回收凭据包括 UPLOADING、CLEANING，清理失败不会释放这部分名额，避免同一账号积累无界孤立对象。逻辑配额保守统计所有已绑定源文件，当前未实现逻辑删除后释放配额；未绑定对象不计入逻辑配额，但受凭据数量限制。对象存储自身的容量、全局用户规模和备份预算仍须独立配置监测。

应用关闭自动 multipart 解析。准入发生在 body 读取前，先校验当前账号、请求类型和 CSRF header 是否存在，获得名额后仍由 Spring CSRF 过滤器核验 token；随后校验归属、版本、配额并持久化上传凭据，再写临时文件。Content-Length 只用于初筛，真实读取时累计字节数，超限立即失败。

嵌入式 Tomcat 配置上传读取空闲超时 15 秒；接收循环在读块之间检查总预算，因此末次阻塞读可能额外占用空闲超时时间，不能宣称精确 120 秒硬截止。OS 磁盘阻塞的端到端硬截止也未验证。生产反向代理必须设置请求大小、连接/速率、空闲时间及总预算；若开启代理请求缓冲，应用准入不能保护代理临时磁盘，需单独预算或关闭该路由缓冲。WAR/其他 Servlet 容器不在当前验证范围。

临时文件在成功/异常路径关闭并删除；每分钟扫描最多 100 个同前缀临时文件，清理超过 1 小时且非本进程活跃的残留。单实例可预估至少并发数 × 文件上限 + 低水位的临时目录容量，Worker 目录及真实存储数据另算。生产应将目录隔离为应用私有目录，设置文件系统容量限制；本机低水位检查不能代表远程 MinIO 磁盘也充足。

SDK 使用 5 MiB 分片，HTTP 连接/读写超时、跨请求整体预算均有界，不把整个视频读入 JVM；复制缓冲为 64 KiB。真实 MinIO 传输测试已覆盖私有桶、上传、下载和签名 Range；堆峰值、分片异常、断连回收、慢网和容量边界仍需压测。

## 事务与补偿

顺序：账号/作品准入 → 短事务创建 UPLOADING 凭据与随机对象键 → 事务外暂存并写对象 → 短事务按账号、作品、凭据顺序锁定并复核 → 源文件记录、作品版本、PENDING 任务、BOUND 凭据和审计一起提交。任何一步失败均不返回伪造成功。

失败后不立即按猜测删除对象：提交结果未知时可能已经绑定，存储超时后也需要留下安全余量。每分钟选取最多 10 条过期 UPLOADING/CLEANING 凭据，事务内锁定、核对无引用并领取 180 秒清理租约，事务外删除对象，成功后记 DELETED。BOUND 或存在媒体引用时保留对象；被清理领取的凭据不能再绑定。删除失败保留凭据，到期后重试。对象键从不复用，因此重复删除同一孤立对象是安全的。

当前数据库凭据/审计记录的长期归档策略尚未实现；维护作业停止或存储持续不可用时需告警和人工处理。不能把测试中的定时清理成功视为真实灾备演练完成。

## 验证边界

集成测试使用 H2、真实 Spring Security 及嵌入式 Tomcat；业务存储故障注入使用内存适配器，SDK 测试使用本机 HTTP 契约服务。覆盖并发竞争、配额、清理与绑定互斥、审计回滚、提交成功后响应异常，以及缺身份/CSRF/越权时 body 尚未读取。微型 MP4 测试体仅用于文件头和传输测试，不作为可解码视频样本。

历史回归已在 MySQL 8.4.3、RabbitMQ 4.0.5 和 FFmpeg 9.0.2 完成，包含 FFprobe 规格验证、投递与处理。2026-11-18 在 JDK 21 下以测试镜像运行真实 MinIO 上传、下载、签名 Range 和私有桶校验，`MinioMediaIntegrationTest` 通过；同日 MySQL 8.4.3、RabbitMQ 4.0.5、FFmpeg 6.1.1 和 MinIO 测试镜像全量 191 项通过、0 跳过。正式服务端版本与许可证、生产桶策略、生产代理和容量压测仍待完成；Firefox 真实上传/播放和 Chromium 冒烟已完成。前端上传页面已接入。真实 MinIO 测试启用条件见 [媒体接口说明](public-media-api.md)，SDK 合约测试不能替代真实服务端验证。
