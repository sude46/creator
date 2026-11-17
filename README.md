# 映作视频创作者平台

基于 Vue 3、Spring Boot 3 的 Java Web 课程项目。用户上传自己的视频，平台负责处理、审核与播放，不生成视频。

## 当前进度

第一阶段是账号与权限：注册、Session 登录、退出、CSRF 防护、普通用户与管理员鉴权、禁用账号会话失效、管理员初始化，以及登录注册页面。

注册和登录目前按来源 IP 进行单实例限流，每分钟合计最多 20 次，不信任客户端提供的转发 IP。开发代理或生产 Nginx 下可能共享代理 IP 配额；正式部署需配置受信代理边界或网关限流，多实例时需要共享限流状态。

限流可通过 `AUTH_RATE_WINDOW_SECONDS`（默认 60）、`AUTH_RATE_MAX_ATTEMPTS`（默认 20）、`AUTH_RATE_MAX_ENTRIES`（默认 10000）配置，启动时校验范围。限流窗口有容量上限，过期按时间顺序摊销清理。测试配置放宽普通功能测试的配额，另有独立安全过滤链测试验证 429，不能把测试配额用于生产。

登录、注册及管理员初始化共享实例级认证计算预算，`AUTH_WORK_MAX_CONCURRENT` 默认 2，超额立即返回 `AUTH_BUSY`（429），不排无界等待队列。`AUTH_BCRYPT_STRENGTH` 默认 10、配置范围 10～14，需要根据机器实测调整，不以降低密码保护强度换吞吐。密码哈希在数据库事务外执行，注册最终由单条 INSERT 和唯一索引保证原子性；Session 认证方式保持不变。

视频后端已接入草稿、私有源文件上传与补偿、RabbitMQ 可靠投递、独立 FFprobe/FFmpeg Worker、处理状态查询与重试、审核、公开查询和短期媒体授权。相关配置见 [Worker](docs/processing-worker-setup.md)、[作者接口](docs/creator-video-api.md)、[审核](docs/video-review-api.md) 与 [媒体授权](docs/public-media-api.md)。上传/派发/媒体等功能需显式配置启用，真实 MinIO 和完整浏览器播放仍待验证。

创作中心前端已开放 `/studio` 和作品详情，接入创建、分类选择、上传、处理状态/重试、编辑、送审/撤回、上下架、本人预览和原因历史，见 [创作中心使用与验收](docs/creator-center-ui.md)。页面使用真实接口；数据库 READY 测试样本仅用于隔离环境验收，不作为模拟数据内置到页面。管理员页面、公开首页/搜索/播放页及收藏前端仍待完成，首页目前为工作台入口。

分类管理后端已完成：管理员创建、修改、启停、删除；已登录用户分页读取启用分类。名称先做 NFKC 规范化和首尾去空白，再按大小写不敏感的规范键保证唯一；修改和删除必须提供版本号，冲突返回 409。分类变更和操作审计同事务提交，审计失败则业务变更回滚。分类管理前端尚未接入。

上下架与封禁后端已接入：作者下架及未修改内容再次发布，管理员对完整 READY 作品强制下架/封禁、解禁后重新审核，作者和管理员查询原因历史。状态和原因审计同事务，版本冲突不会重复执行，见 [上下架与封禁](docs/publication-moderation-api.md)。处理中的治理、前端和真实浏览器仍未接入。

收藏后端支持添加、取消、本人关系状态和分页列表，重复收藏保持原排序，重复取消也成功。不可见作品只显示可取消的占位，批量复用公开查询条件；管理员不能操作其他用户的收藏。接口、幂等和并发边界见 [收藏设计](docs/favorite-api.md)。本模块没有新增配置或依赖。

### 分类 API

| 方法与地址 | 权限与参数 |
| --- | --- |
| `GET /api/categories` | 登录用户，仅启用分类；`page=1&size=20` |
| `GET /api/admin/categories` | 管理员，含停用分类；相同分页参数 |
| `POST /api/admin/categories` | 管理员；JSON：`name`、`sortOrder` |
| `PUT /api/admin/categories/{id}` | 管理员；JSON：`name`、`sortOrder`、`enabled`、`version` |
| `DELETE /api/admin/categories/{id}?version=0` | 管理员；版本必须匹配 |

写请求沿用 Session + CSRF。名称规范化后为 1～32 个 Java 字符，不含控制字符；排序值 0～10000。页码为 1～1000，每页 1～50 条，按排序值、ID 升序，返回 `{items,total,page,size}`；ID 为字符串，版本为数字。创建返回 201，删除返回 204；名称重复或版本过期返回 409，记录不存在返回 404。重复创建会报重名冲突，不自动视为成功；同版本重复修改/删除不会重复写审计。

### 作品草稿 API

| 方法与地址 | 说明 |
| --- | --- |
| `POST /api/creator/drafts` | JSON：`requestKey`、`title`、`description`、`categoryId`；返回 201 |
| `GET /api/creator/drafts` | 本人草稿，`page=1&size=20`；按 ID 倒序，分页上限与分类相同 |
| `GET /api/creator/drafts/{id}` | 本人草稿详情 |
| `PUT /api/creator/drafts/{id}` | JSON：`title`、`description`、`categoryId`、`version`；返回更新结果 |

所有入口要求登录，写请求带 CSRF。所有权来自当前账号，不接受 `ownerId`、状态或其他额外字段；他人作品（含管理员访问）统一返回 404。响应为 `{id,categoryId,title,description,version}`，两个 ID 均为字符串。

标题去除首尾空白后 1～120 个 Java 字符；简介去除首尾空白后最多 2000 个字符，可为空，允许换行和制表符。拒绝非法控制字符和不完整的 Unicode 字符。正文仍是纯文本，前端接入时不能作为 HTML 渲染。

每次新建由客户端生成随机 UUID `requestKey`（例如 `crypto.randomUUID()`），重试保留同一键和相同内容，不为每次重试重新生成。相同账号、相同键、相同规范化创建内容只产生一个作品和一条创建审计；同键不同内容返回 `REQUEST_KEY_CONFLICT`（409）。键按账号隔离，大小写形式统一；内容指纹始终保存首次创建内容，后续编辑不覆盖。成功重放仍返回 201 和现存草稿的最新可读内容。若作品已删除、被封禁或进入后续流程，则重放返回 404，不重建作品。

新建/更换分类只接受启用分类，失败返回 `CATEGORY_UNAVAILABLE`（409）；原分类已停用时可继续编辑文本或重放原创建请求。被引用分类不可物理删除，返回 `CATEGORY_IN_USE`（409），可改为停用。编辑要求匹配版本，过期返回 `STALE_WRITE`（409），应重新读取并由用户确认内容，不盲目覆盖重试。草稿与审计同事务提交；失败后请求键也回滚，可原样重试。草稿写事务设置 5 秒超时，实际锁等待及连接池行为仍需真实 MySQL 验证。

### 视频源文件上传

配置方法、资源预算、接口与失败处理见 [上传与对象存储接入](docs/upload-storage-setup.md)。上传默认关闭，不需要存储服务即可运行账号、分类和草稿模块；开启后启动时核验私有桶、关闭版本化及未完成分片回收规则，检查不通过则停止启动。

`PUT /api/creator/videos/{id}/source` 直接以 `video/mp4` 请求体发送文件，携带 Session Cookie、`X-CSRF-TOKEN` 和 `X-Video-Version`，不使用 FormData。`GET /api/creator/videos/{id}/upload` 查询本人作品是否已绑定源文件及当前版本。响应不返回内部对象键或存储地址。默认文件上限 200 MiB、实例并发 2、每账号 1；文件头初筛通过仅表示等待处理，实际编码/时长仍须后续 Worker 验证。

### RabbitMQ 可靠投递

接入及恢复语义见 [任务可靠投递](docs/processing-dispatch-setup.md)。设置 `PROCESSING_DISPATCH_ENABLED=true` 和 RabbitMQ 应用账号后启用；上传服务与派发器可独立启停。任务先提交数据库再发送消息，broker 不可用时保留 PENDING 并退避重试；仅 confirm ACK 且没有 mandatory return 才记录投递成功。Worker 通过独立进程消费，投递成功不代表视频处理成功，只有媒体结果绑定事务完成才进入 READY。

## 工程结构

```text
backend/      Spring Boot API，按业务模块组织
frontend/     Vue 3 + TypeScript + Element Plus
deploy/       本地基础设施配置
```

账号模块现已按 `web → application → domain` 组织，MyBatis 与 Spring Security 适配位于 `infrastructure`，管理员初始化入口位于 `bootstrap`。Controller 不直接访问 Mapper，注册与管理员初始化是两个独立用例；密码与用户名业务规则对 HTTP 和内部调用同样生效。

## 环境要求

- JDK 21 为目标版本，Maven 3.9 或以上。当前工程可用较新 JDK 编译为 Java 21 字节码，正式运行仍需在 JDK 21 上验证。
- Node.js 22.14 或以上，npm。
- Docker Desktop/Engine 和 Compose，或可用的 MySQL 8。

依赖版本在 Maven 配置、package.json 和锁文件中固定，是开发基线；上线前需要复核支持周期和安全更新。

## 本地启动

在项目根目录复制环境模板并修改本机密码：

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/compose.yml up -d
```

MySQL 使用本机端口 3307，RabbitMQ 使用 5672，管理页面使用 15672。端口仅绑定 127.0.0.1。账号模块不依赖 RabbitMQ，只启动数据库可在命令末尾加 `mysql`；启用任务派发时再配置 Java 进程的 RabbitMQ 环境变量。

后端在独立终端启动。`DB_PASSWORD` 必须与 `.env` 中 `MYSQL_PASSWORD` 一致；Compose 的 `.env` 不会自动导入 Java 进程。

```powershell
cd backend
$env:DB_PASSWORD = '你设置的本机数据库密码'
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' spring-boot:run
```

如果默认 Java 不是 21 或更新版本，请先在当前终端正确设置 `JAVA_HOME` 和 `PATH`。不要把本机路径写死在工程配置中。`maven-settings.xml` 使用 Maven Central HTTPS，避免机器全局的旧 HTTP 镜像影响依赖下载。

前端在另一个终端启动：

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

打开 http://localhost:5173 。前端代理 `/api` 到本机 8080，浏览器通过同源 Cookie 保持会话，不把登录凭据存入 localStorage。

登录后进入“创作中心”。若后端使用其他本机端口，可在启动 Vite 的终端设置 `API_PROXY_TARGET`，例如 `http://127.0.0.1:18080`；开发端口可用 `npm.cmd run dev -- --port 5188` 指定。上传和预览需分别启用后端存储相关配置，关闭时页面显示真实错误，不模拟成功。

生产环境必须配置 HTTPS、`COOKIE_SECURE=true` 和同源反向代理，不直接使用开发服务器。容器镜像与正式媒体存储部署将在对应模块接入时完善。

## 初始化管理员

注册接口只生成普通用户。首次启动前，在后端终端临时设置：

```powershell
$env:ADMIN_USERNAME = 'studio_admin'
$env:ADMIN_PASSWORD = '自行设置的强密码且包含数字'
```

启动时不存在该用户名才创建管理员，不会覆盖已有密码，不会把同名普通用户提升权限。完成初始化后移除这两个环境变量并重新启动：

```powershell
Remove-Item Env:ADMIN_USERNAME, Env:ADMIN_PASSWORD
```

密码要求 10～64 位，含字母和数字，UTF-8 编码不超过 BCrypt 的 72 字节限制。用户名使用 3～32 位字母、数字或下划线，统一存为小写。不要提交真实密码。

## 验证

```powershell
cd backend
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' test
```

账号集成测试使用 H2 的 MySQL 兼容模式，执行实际迁移、Spring Security 过滤链和 Mapper；覆盖注册防提权、密码哈希、重复用户名、CSRF、会话、管理员边界和禁用账号。H2 不代替真实 MySQL 验证，部署前还需要运行真实数据库联调。

```powershell
cd frontend
npm.cmd run build
```

手工验证：注册 → 登录 → 刷新页面保持身份 → 普通用户无管理入口 → 退出后不能访问首页；使用初始化管理员登录可验证管理接口。完整浏览器和 MySQL 联调的实际结果另行记录，不将构建通过视为全部验收通过。

2026-11-16 验证记录：Spring Boot 3.5.16 下 8 项测试通过，前端类型检查与生产构建通过，npm 安装审计报告为 0 个已知漏洞，Compose 配置校验通过。此次使用本机 JDK 23 编译到 Java 21 目标；JDK 21 实际运行、真实 MySQL 和浏览器端完整联调仍待验证，Docker 服务尚未启动。

同日账号重构后：清理旧编译产物并完成回归，最终 18 项测试通过。新增覆盖内部调用校验、大小写用户名、并发重名注册、管理员初始化冲突与幂等、角色变化后的会话失效、真实过滤链限流、404/405/415 与 CSRF 错误，以及数据库故障时拒绝访问并返回 503。测试仍使用 H2，未将结果表述为真实 MySQL 联调完成；本次未修改前端契约或页面。

后续账号资源加固：21 项测试通过，新增认证预算占满时登录/注册返回 429、释放后恢复、异常不泄漏许可、超长凭据在查询与哈希前拒绝，并在实际 Spring 用例调用中验证哈希不占用数据库事务。真实服务器暂未连通：SSH 已到达主机，但现有认证未通过，未修改服务器环境。

分类管理完成后：后端共 28 项测试通过，新增覆盖实际过滤链的管理权限/CSRF、名称规范化冲突、分页边界和稳定排序、停用过滤、过期版本、独立线程竞争更新以及审计失败时创建/修改/删除的事务回滚。迁移 V2 在 H2 MySQL 兼容模式执行通过；真实 MySQL 的迁移、索引与并发行为仍待验证，尚无压测结论。

作品草稿阶段：后端 38 项测试通过，新增 10 项集成测试覆盖本人归属与管理员边界、并发重复创建、同键异内容冲突、编辑后重放、并发版本竞争、停用分类语义与真实作品外键保护、审计失败后的整体回滚、分页/输入边界、删除/封禁/状态变化后的访问与重放保护。V3 迁移在 H2 上执行通过；未将这些结果视为真实 MySQL、浏览器或生产容量验收。

上传阶段的验证包括真实嵌入式 Tomcat + HTTP 客户端的 Session/CSRF 文件流请求、H2 V4 迁移、存储写入不占数据库事务、双线程绑定竞争/配额、失败回滚、提交成功后响应丢失的对象保护、清理重试、临时磁盘/截止时间，以及真实 MinIO SDK 对本机 HTTP 契约端点的签名、私有桶检查、上传和删除。这里的存储端点是测试服务，不是真实 MinIO；真实 MySQL/MinIO、网络慢读边界、服务器资源预算与完整浏览器联调仍待完成。

2026-11-17 可靠投递阶段：在独立 MySQL 8.4.3 与 RabbitMQ 4.0.5 容器上，完整后端 81 项测试全部通过，无失败或跳过；V1～V5 迁移实际执行通过。真库回归修复草稿查询的 MySQL 保留字别名问题，覆盖账号、分类、草稿、上传、派发租约竞争及真实 broker 的确认、退回、队列满拒收、TTL 死信和首次连接拓扑声明。前一轮 H2 + 真实 RabbitMQ 同样 81 项通过。Flyway 对 MySQL 8.4 提示超出该版本已测试范围，迁移本次实际通过，正式部署仍需复核版本支持。测试使用 JDK 23，目标字节码 Java 21；JDK 21 运行、真实 MinIO、媒体 Worker、浏览器完整流程和生产容量仍待验证。

同日媒体 Worker 阶段：H2 与真实 MySQL 两轮完整回归各 104 项通过，无失败或跳过，均包含真实 RabbitMQ 和 FFmpeg。新增验证执行租约竞争、自动重试上限、旧令牌/代次保护、事务回滚、已上传但响应失败的封面清理、严格消息协议、进程超时与输出限制，以及独立非 Web Worker 的真实消费和结果绑定。实际工具为 FFmpeg 9.0.2，二进制保存在忽略的本地缓存；真实 MinIO、操作系统沙箱、JDK 21 实际运行和生产容量尚未验收，不能将本地样本测试视作公网媒体处理已具备全部隔离。

作者处理查询与手动重试阶段：新增 9 项 H2 集成测试通过，真实 MySQL + RabbitMQ + FFmpeg 全量 113 项通过；覆盖本人边界、CSRF/输入、并发新代次、与上传共用待处理配额、旧消息/执行结果隔离及审计失败回滚。

审核阶段：真实 MySQL + RabbitMQ + FFmpeg 全量 132 项通过，无失败/错误/跳过，V7 迁移通过。审核集成用例包括 5 次双管理员竞争验证；真库发现并修正驳回写历史时分类外键锁与通过流程的反向锁序。审核结果、发布状态、内容快照和审计保持同事务；媒体预览、公开播放、审核前端仍未接入。

公开查询与媒体授权阶段：新增统一可见性、字面标题搜索、分类筛选、稳定分页和三类媒体授权；V8 增加发布状态/时间/ID 索引。配置与测试边界见 [媒体接口说明](docs/public-media-api.md)。真实 MinIO 候选镜像因仓库访问拒绝未启动，相关真实存储测试保留显式跳过；已核对候选源码标签服务端许可为 AGPL v3，正式版本与部署仍待确认。

本阶段最终真实 MySQL + RabbitMQ + FFmpeg 回归共 147 项，146 项通过、1 项真实 MinIO 测试跳过，零失败/错误；V8 在 MySQL 8.4.3 执行通过。新增公开/授权集成测试 12 项、原生 SDK 签名测试 2 项均通过；此前 H2 上公开/授权 11 项及 SDK 2 项通过。Flyway 对 MySQL 8.4 的版本支持提示仍存在，JDK 21 运行与容量没有新增验收结论。

上下架与封禁阶段：新增专项最终 H2、真实 MySQL 各 19 项通过；真实 MySQL + RabbitMQ + FFmpeg 最终完整回归共 166 项，165 项通过、1 项真实 MinIO 测试跳过，零失败/错误。V9 实际迁移通过，既有账号、草稿、上传、审核和媒体授权回归保持通过。首轮 H2 在已有审核提交准备阶段出现一次 5 秒事务超时，保持原时限复跑后通过，未据此声称容量达标；JDK 21 实际运行、存储与浏览器边界继续待验证。

2026-11-17 收藏阶段：新增 H2 专项 14 项通过；最终真实 MySQL 8.4.3 + RabbitMQ 4.0.5 + FFmpeg 9.0.2 全量 180 项，179 项通过、1 项真实 MinIO 测试跳过，零失败/错误。V10 实际迁移通过，包含三次并发重复收藏和收藏与下架竞争；原模块同步补充收藏外键清理顺序。运行使用 JDK 23 编译到 Java 21，真实 JDK 21、浏览器、存储和容量验证仍待完成。

## 后端设计审核

2026-11-17 创作中心前端阶段：类型检查及生产构建通过；真实 Spring Boot/MySQL 浏览器验证创建、编辑、列表回显、送审/撤回和版本冲突保留输入，上传/媒体关闭时的错误反馈，以及390像素窄屏。送审使用隔离数据库 READY 样本，真实对象上传/媒体预览、其余按钮逐项浏览器验收及跨浏览器兼容仍待完成，详细记录见 [验收边界](docs/creator-center-ui.md)。本轮未修改后端，未重新执行后端180项回归；上一模块结果仍为历史验证记录。

后端分层、模块归属、现有问题、设计模式、15 条业务链路及审核清单见 [后端详细设计](docs/backend-design-review.md)。其中未来模块为设计契约，不能当作已实现能力。先重构账号边界，再逐模块实现视频流程。

安全、资源准入、性能预算、部署与验收要求见 [安全与性能设计优化](docs/security-performance-review.md)，明确区分已实现措施与待落实事项；性能数值为待验证目标，不代表已有压测结果。

## Git 提交与同步

每完成一个模块，先验证再独立提交并推送 GitHub。Gitee 按最新要求暂缓。原需求设计文档 `docs/视频创作者平台需求分析与架构设计.md` 按要求不提交，其他必要工程文档正常管理。`.env`、密钥、视频、构建产物与本地依赖缓存均忽略。

当前 GitHub 远程为 `https://github.com/sude46/creator.git`。克隆时远程可能叫 origin，先用 `git remote -v` 确认；本地工程使用 github 名称：

```powershell
git push github main
```

推送失败应保留本地提交并核对错误后重试，不用强制推送掩盖差异。以后恢复 Gitee 时再配置已确认的远程，不把密码或 token 写进仓库地址。
