# 媒体 Worker

## 运行边界

同一工程使用 `worker` profile 启动独立进程，不开 HTTP 端口，不初始化管理员，不在 API 中执行媒体命令。默认 API 不创建 Worker。API 上传和派发与 Worker 不能在同一个进程开启；首版每实例单消费者、prefetch 1、并发 1。

执行租约负责跨实例互斥，实例内另有单任务准入。数据库短事务按作品 → 任务 → 输出登记的顺序锁定；下载、FFprobe、FFmpeg、上传及对象删除均在事务外完成。Worker 数据库连接池默认 2，连接等待 5 秒，可用 `WORKER_DB_MAX_POOL_SIZE` 调整，生产值需结合真实数据库验证。

## 启动

先安装经过核验的原生 FFprobe、FFmpeg，配置与 API 相同的数据库、私有存储以及 RabbitMQ vhost。沿用 [上传配置](upload-storage-setup.md) 的私有桶要求，封面使用同一个私有桶中的 `covers/` 前缀，应用账号必须拥有对应的读写和删除权限。版本控制必须关闭；源文件的未完成分片由 `STORAGE_INCOMPLETE_UPLOAD_CLEANUP` 选择 MinIO 服务端回收或 S3 bucket lifecycle，不能对当前 MinIO 测试版本使用仅 AbortIncompleteMultipartUpload 的规则。封面上限 1 MiB，使用单对象上传。

在 `backend` 目录运行：

```powershell
$env:DB_PASSWORD = '本机数据库密码'
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '5672'
$env:RABBITMQ_USER = '应用账号'
$env:RABBITMQ_PASSWORD = '本机MQ密码'
$env:STORAGE_ENDPOINT = 'http://127.0.0.1:9000'
$env:STORAGE_ACCESS_KEY = '应用存储账号'
$env:STORAGE_SECRET_KEY = '应用存储密码'
$env:FFPROBE_PATH = 'D:\tools\ffmpeg\bin\ffprobe.exe'
$env:FFMPEG_PATH = 'D:\tools\ffmpeg\bin\ffmpeg.exe'
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' spring-boot:run '-Dspring-boot.run.profiles=worker'
```

工具路径必须是绝对路径，不能是 `.cmd`/`.bat`；命令使用参数数组，不经过 shell。启动检查原生工具可执行及私有桶约束，通过后才启动消费者和恢复扫描。Java 进程环境变量需要单独设置，Compose 的 `.env` 不会自动导入。使用自定义 vhost 时设置 `SPRING_RABBITMQ_VIRTUAL_HOST`，账号配置/读/写权限限定到 `creator.processing` 前缀拓扑。

API 进程仍需启用 `PROCESSING_DISPATCH_ENABLED=true` 才能将待处理任务发进队列；Worker 自己不发布任务。恢复扫描会将到期的重试任务恢复为 PENDING，API 派发器继续运输。

## 状态与幂等

1. 消息只能包含四个整数字段 schemaVersion、taskId、videoId、generation，总长不超过 1024 字节。拒绝浮点、字符串数字、未知字段、重复字段、尾随 JSON、溢出 ID 和未知版本。
2. 领取事务核对任务/作品身份、当前代次、源文件绑定、作品状态和账号有效性。已完成、有效租约中的重复消息及旧代次不再执行；未知任务或身份不匹配进入死信。
3. 成功领取后增加执行次数、生成 executionToken、设置 RUNNING 和作品 PROCESSING，同时登记该令牌专属封面对象键。租约有效时才允许提交。
4. 源文件从私有桶流式拉到专属临时目录，实际大小必须等于数据库记录，不能用对象存储返回的长度放宽预算。
5. FFprobe 校验通过才执行截图。封面上传后，完成事务重新核对作品、代次、有效执行令牌和未被清理领取的输出登记；封面资源、时长/尺寸、任务 SUCCEEDED、作品 READY 一起提交。审核和发布状态不变。
6. 成功后 ACK 丢失可再次投递，但不会再次执行或绑定。完成失败/结果未知时不会在 catch 中直接删除对象：输出登记及媒体引用负责后续判断，避免删除实际上已提交的封面。

数据库时间写入统一到 UTC 微秒，与 TIMESTAMP(6) 精度一致，避免恢复为 PENDING 后因纳秒舍入而暂时无法领取。派发也只发送作品当前 processing_generation 的任务。

## 重试、崩溃和清理

永久错误（坏媒体、不支持规格）直接 FAILED；存储暂时故障、进程失败/超时和临时目录错误进入 RETRY_WAIT，最多执行 3 次，包含首次执行。退避约 20、40 秒并加 0～5 秒抖动，次数达到上限后 FAILED。消费者不通过 sleep 或立即重新入队重试。

每 5 秒独立扫描最多 10 个到期 RUNNING/RETRY_WAIT 任务。执行租约到期按失败策略处理；重试到期恢复 PENDING，再由派发器投递。完成和恢复持有相同顺序的行锁，旧令牌不能覆盖新的执行结果。删除、封禁、账号停用或代次变化后的过期任务取消，避免重新绑定资源。

消息结构错误及未被正常持久化的基础设施异常使用 reject、不立即重新入队，由有界死信保留诊断；任务仍依据数据库 PENDING 或执行租约恢复。数据库故障不能把未完成状态当成功 ACK，也不进入无界的 requeue 忙循环。主/死信容量及保留限制沿用可靠投递配置。

领取时已登记封面键，进程即使在上传后崩溃也能发现遗留对象。输出登记在租约后额外保留 120 秒；维护扫描每轮最多领取 10 个过期对象，标记 CLEANING 后事务外删除，再标记 DELETED。已绑定输出永不进入清理；清理中的输出不能再被绑定；删除失败保留记录，120 秒后重试。

临时目录正常路径在 finally 清理；崩溃遗留按年龄清理，每轮最多检查 100 个目录，不跟随符号链接。临时目录必须为 Worker 专用，每个实例使用不同目录。关闭 Worker 会中断当前任务，媒体进程在 finally 中强制终止并关闭输出管道；未完成数据库状态由租约恢复。

作者手动重试通过 [作者处理接口](creator-video-api.md) 提供，只有可重试的终结失败可创建新 generation；Worker 内自动重试和过期恢复仍使用原 generation。

## 格式和资源限制

| 项目 | 当前约束 |
| --- | --- |
| 容器/轨道 | MP4；1 条视频，0～1 条 AAC 音频；不接受字幕、数据等其他轨道 |
| 视频 | H.264、yuv420p，帧率大于 0 且不超过 60 |
| 分辨率 | 每边不超过 1920、总像素不超过 1920 × 1080，支持竖屏 |
| 音频 | AAC、1～2 声道、8～48 kHz |
| 时长/源大小 | 大于 0、不超过 600 秒；源文件不超过 200 MiB |
| 封面 | 首帧 JPEG，宽 640、等比例偶数高度，上限 1 MiB |
| 总任务/执行租约 | 300 秒 / 360 秒；租约至少超过总预算 60 秒 |
| FFprobe/截图 | 15 秒 / 45 秒，均不得突破任务整体截止时间 |
| 媒体输出 | stdout/stderr 各最多收集 64 KiB，超限终止，不记录原始输出 |
| 线程 | 消费并发 1，媒体解码/编码/滤镜各明确限制为 1 |
| 临时磁盘 | 准入时要求至少 1 GiB 余量，再预留 200 MiB 源文件和 1 MiB 封面 |

工具参数只允许 file 输入协议、mov 解析器；显式关闭 MP4 外部数据引用和绝对外部路径。子进程环境只传 SystemRoot（Windows）、TMP、TEMP、LANG，不继承数据库、MQ 和存储凭据。

这些是应用层限制。本地原生进程没有经过操作系统级文件系统/网络/内存沙箱验证，磁盘检查也不是卷配额，不能据此宣称解析器漏洞被完全隔离。接收公网不可信媒体前，部署模块仍需独立低权限身份、最小挂载、CPU/内存/进程数/磁盘配额及进程网络隔离；不得给 Worker 挂载宿主 Docker 管理套接字。当前验证使用本地生成的受控样本，不视为生产隔离验收。

## 测试

默认测试包含 H2 状态机/并发/失败补偿、消费协议、子进程超时/输出边界和真实 MinIO SDK 的 HTTP 合约；真实工具和 broker 测试需显式设置测试环境变量，否则跳过。

```powershell
$env:CREATOR_RABBIT_IT_PORT = '5673'
$env:CREATOR_FFMPEG_IT_DIR = 'D:\tools\ffmpeg\bin'
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' test
```

测试 broker 必须为专用实例，账号 creator_test / integration-only-password；除投递模块的 vhost 外还需 creator_worker_it，授权 creator.processing 前缀配置/读/写权限。完整 Worker 用例在非 Web 上下文中实际消费 RabbitMQ，执行真实 FFprobe/FFmpeg，绑定结果并验证毒消息死信；对象存储使用故障可控适配器，真实 MinIO 测试镜像另行运行。SDK 合约端点不是真实 MinIO 部署。使用真库测试时通过 SPRING_DATASOURCE_* 环境变量指定独立测试库，绝不能使用业务数据库。

本次工具来自 FFmpeg 官网列出的 Gyan Windows 构建，实测版本 9.0.2，下载压缩包 SHA-256 为 `60F467265B1E312373DBCD92200C2618A74850F98D3D078E94296BB3FA2047BA`；二进制留在忽略的本地缓存，不入 Git。此记录用于复现，不表示未来下载的同名 release URL 内容不变；部署应固定经验证版本并复核许可证及安全更新。
