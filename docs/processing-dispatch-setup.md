# 任务可靠投递

## 当前能力

上传绑定事务已写入 processing_task。本模块定量扫描可发送的 PENDING 任务，先以条件更新领取数据库派发租约，事务结束后发送 RabbitMQ 消息，再用派发令牌写回确认结果。数据库是任务事实来源，RabbitMQ 是运输通道；没有把数据库和 MQ 组合成跨服务原子事务。

本文描述 producer/派发恢复；后续独立 [Worker](processing-worker-setup.md) 已接入消费、执行租约、FFprobe、FFmpeg 和自动重试。processing_task.dispatch_attempts 只统计派发次数，不是视频执行次数。单纯投递成功不会改变 processing_task 或视频处理状态，审核及发布状态也不改变。

## 启用

沿用项目 Compose 中的 RabbitMQ 服务，创建只允许访问本应用 vhost 和处理拓扑的专用账号。现有镜像 rabbitmq:4.0.5-management 是联调基线，不代表已复核为生产维护版本；正式部署应复核维护周期、升级并回归。端口不向公网开放，跨主机连接使用经过验证的私有网络或 TLS。

启动后端的 PowerShell 设置：

```powershell
$env:PROCESSING_DISPATCH_ENABLED = 'true'
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '5672'
$env:RABBITMQ_USER = '替换为应用账号'
$env:RABBITMQ_PASSWORD = '替换为应用密码'
```

自定义 vhost 可用 Spring Boot 的 `SPRING_RABBITMQ_VIRTUAL_HOST` 配置，默认为 `/`。账号需有下列拓扑的配置/读/写权限；开启派发但缺少主机、有效端口或账号密码会阻止启动。数据库迁移 V5 自动追加派发字段，不修改已提交的 V1～V4。

启动上传服务不强制开启派发器，关闭派发器不删除已上传对象或数据库任务；重新开启会从持久化任务恢复。不需要启用上传或连接 MinIO 就可以派发已有任务。

## 拓扑和消息

| 项目 | 默认值与行为 |
| --- | --- |
| 主交换机 | creator.processing，持久化 direct |
| 主队列 | creator.processing.jobs，持久化 classic |
| 路由键 | video.process |
| 死信交换机 / 队列 | creator.processing.dead / creator.processing.dead.jobs，均持久化 |
| 死信路由键 | video.process.dead |
| 主队列限制 | 默认 1000 条、1 MiB 消息体预算；超过容量 reject-publish |
| 主队列 TTL | 默认 300 秒，过期进入死信队列 |
| 死信限制 | 默认 1000 条、1 MiB、24 小时 TTL；诊断记录按队列上限回收，不重新路由成循环 |

原有同名队列参数不匹配时 RabbitMQ 会拒绝重新声明，不会在线自动修改；更改 TTL、容量或队列类型应先制定队列迁移方案。本项目不会自动删除已有业务队列。

消息体仅含 `{"schemaVersion":1,"taskId":123,"videoId":456,"generation":1}`，消息为 persistent。messageId 为 taskId:generation，用于稳定识别重复任务；每次派发的 correlationId 使用独立 UUID 派发令牌。消息不含源文件、对象键、凭据或签名 URL，接收方不能把 messageId 当作已经执行过的证明。

RabbitAdmin 与 RabbitTemplate 的专用 publisher connection 对齐，首次创建该连接就声明拓扑；不能依赖另一个从未连接的默认连接先建队列。RabbitAdmin 不叠加多轮初始化重试，失败交给数据库派发退避。publisher confirm 使用 correlated，启用 mandatory 和 return；ACK 与 return 同时出现时仍判定 UNROUTABLE。

## 租约、确认和恢复

1. 只扫描数据库到期任务，排除源文件未绑定、作品删除/封禁、账号不可用或处理状态已变化的记录。扫描结果不是执行许可，领取时再次条件更新校验。
2. 领取语句同时要求 PENDING、next_dispatch_at 到期、旧租约为空/过期，写入新令牌并递增派发次数；两个独立连接竞争只能一方成功。
3. 发送在数据库事务外进行。confirm ACK 且无 return 才写 published_at，并延后 next_dispatch_at；这是“最近一次确认发送”的时间，不代表已消费或已处理。
4. NACK、return、确认超时、broker 故障和中断都保留 PENDING，并按 5、10、20、40…秒退避，默认上限 300 秒。派发次数没有固定总上限，避免长时间 broker 故障后永久丢弃任务；单次扫描、队列、连接、确认等待和退避均有界。
5. 写回确认/失败必须匹配未过期的派发令牌，过期或已被新租约替代的令牌不能覆盖新结果。若未来消费者已将任务领取成 RUNNING，迟到的派发确认也不能把它改回待发送。
6. 发送成功后进程崩溃、数据库写回失败或提交结果未知时，旧租约到期后允许重复发送。即使已有 published_at，只要 PENDING 超过默认 600 秒仍会重新派发；这样消息过期、队列丢失或从未被领取都可恢复。

重新派发间隔必须大于消息 TTL 并留至少 30 秒余量，避免长期无 Worker 时每轮扫描都往队列堆副本。死信队列仅作有限诊断，任务恢复仍以数据库为准。消费者通过数据库执行租约、generation 和执行令牌保护重复消息，不能宣称 exactly-once。

## 有界运行

| 环境变量 | 默认值 | 范围 |
| --- | --- | --- |
| PROCESSING_DISPATCH_ENABLED | false | 显式开启 |
| PROCESSING_DISPATCH_BATCH_SIZE | 10 | 每轮 1～50 个任务 |
| PROCESSING_DISPATCH_SCAN_MS | 5000 | 1000～3600000 毫秒 |
| PROCESSING_CONFIRM_SECONDS | 5 | 确认等待 1～10 秒 |
| PROCESSING_DISPATCH_LEASE_SECONDS | 45 | 25～120 秒，至少为确认等待 + 20 秒 |
| PROCESSING_REDRIVE_SECONDS | 600 | 60～3600 秒，至少为消息 TTL + 30 秒 |
| PROCESSING_MESSAGE_TTL_SECONDS | 300 | 30～600 秒 |
| PROCESSING_MAX_QUEUE_MESSAGES | 1000 | 主队列及死信队列各 1～10000 条 |
| PROCESSING_DISPATCH_RETRY_MAX_SECONDS | 300 | 派发退避上限 5～600 秒 |

调度器每实例仅一条专用线程，与上传维护作业分开，不创建无界异步任务队列；各任务失败不终止后续扫描。数据库写事务超时 5 秒，NIO 写帧队列上限 64、等待入队 5 秒，连接/握手/通道 RPC 分别限 5 秒，心跳 30 秒。channel cache/checkout 为 4 / 5 秒，协商 channel 上限 16；确认等待从发送返回后计算，不是完整网络操作的硬截止。拓扑初次声明和多段网络等待可能超过候选派发租约，令牌校验负责拒绝迟到结果，不以此宣称端到端时限已实测达标。

多实例各有调度线程，任务归属由数据库条件更新协调；当前不使用 Redis 或分布式调度框架。索引顺序为 state、next_dispatch_at、id、dispatch_lease_until，匹配扫描顺序并过滤租约；生产数据规模下的执行计划和延迟仍需实测。当前只有结构化任务 ID/错误类别日志，指标、积压告警和运营页面尚未接入。

## 验证方法

默认 `mvn test` 运行 H2、可控时钟/故障注入及确认适配测试；真实 broker 的 5 项测试在没有专用测试环境时明确跳过，不将跳过计算为通过。

真实 broker 测试只允许指向独立测试实例：本机端口由 `CREATOR_RABBIT_IT_PORT` 指定，使用测试账号 creator_test / integration-only-password；配置测试还需独立 vhost creator_config_it，并授权 creator.processing 前缀的配置/读/写权限。这些是隔离测试凭据，不用于开发或生产。测试会创建、清空及删除测试拓扑，不应指向业务 broker。

```powershell
$env:CREATOR_RABBIT_IT_PORT = '5673'
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' test
```

真实 broker 用例覆盖 persistent 消息确认/接收、mandatory return、队列满时 NACK、TTL 死信，以及生产配置首次发布时的自动声明。数据库用例覆盖双线程租约竞争、失败退避、旧令牌保护、发送后崩溃、批量上限，以及上传绑定提交前不可投递、提交后可投递。H2 测试显式使用 UTC，与应用时钟及生产 MySQL 连接时区保持一致。

2026-11-17 实测：独立 MySQL 8.4.3 + RabbitMQ 4.0.5 环境下，完整 81 项测试通过，0 失败、0 错误、0 跳过；V1～V5 迁移在真库执行通过。真库测试发现并修复草稿 SQL 的保留字别名兼容问题。此前 H2 + 真实 RabbitMQ 的 81 项也全部通过。运行环境为 JDK 23、编译目标 Java 21；Flyway 提示 MySQL 8.4 超出该版本已测试范围，需在部署前复核支持版本。

真库回归可在 PowerShell 中用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_DRIVER_CLASS_NAME`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖测试数据源，再执行上述测试命令。URL 使用 UTC 会话时区；Windows 的 Maven 批处理可能二次解析命令行 URL 中的 `&`，因此优先通过环境变量传入。测试会清理业务表，只能指向独立测试库，不能指向开发或生产数据。

不把这些结果视为 RabbitMQ 重启/磁盘损坏演练、MySQL 故障切换、真实 MinIO、媒体 Worker、服务器容量或生产压测验收。
