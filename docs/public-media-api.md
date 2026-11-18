# 公开视频查询与私有媒体授权

## 范围与接口

当前所有 API 沿用 Session 登录，包括首页和公开作品查询。“公开”表示平台登录用户可见。媒体链接签发使用 POST 并校验 CSRF；客户端不能指定对象键、桶、播放用途或有效期。

| 接口 | 行为 |
| --- | --- |
| `GET /api/videos?title=&categoryId=&page=1&size=20` | 已发布作品列表，标题包含匹配，可按分类过滤 |
| `GET /api/videos/{id}` | 当前公开详情，不可见返回 404 |
| `POST /api/videos/{id}/media?kind=SOURCE` | 公开作品源视频；`kind=COVER` 为封面 |
| `POST /api/creator/videos/{id}/preview?kind=SOURCE` | 作者本人预览已处理完成的作品，可选 COVER |
| `POST /api/admin/reviews/{id}/preview?kind=SOURCE&version=3&reviewRound=2` | 管理员预览当前待审轮次，可选 COVER，禁止自审 |

列表返回 `{items,total,page,size}`，作品投影包含 id、authorId、authorName、categoryId、categoryName、title、description、durationMillis、width、height、publishedAt。ID 为字符串；publishedAt 使用现有 UTC 时间约定。响应不含审核原因、执行令牌、源对象键或存储凭据。

标题去首尾空白后最多 120 个 Java 字符，不接受控制字符及非法 UTF-8；空标题不筛选。`%`、`_`、`!` 按字面匹配，所有输入参数化绑定。categoryId 为正整数；分页默认 20、最多 50 条，最多 1000 页。按 publishedAt、id 倒序稳定排序。标题包含匹配为首版小规模 LIKE，实现不等于已有全文索引或大规模性能结论。

## 可见性与并发边界

列表、详情和公开媒体授权共用公开条件：作者有效、作品未删除/封禁、READY、当前处理代次成功、已绑定源文件和封面、媒体元信息存在、审核 APPROVED、发布 PUBLISHED 且发布时间存在。分类停用阻止新选择和审核通过，不自动隐藏已经发布的作品。

作者预览要求本人所有权及完整处理结果；管理员身份不能绕过作者预览边界。审核预览还要求 ADMIN、非本人、当前 PENDING、精确 version/reviewRound；撤回、重提或决定后旧参数不再允许签新链接。内部签发用例也重新验证账号和角色快照，不只依靠 HTTP 过滤器。

授权查询先于签名；无权限时不调用签名器。签名在数据库事务外执行，没有对象存储 HTTP 请求持有数据库锁。列表和详情只读；签发没有业务状态变更或补偿记录，重复请求可得到新的短期链接。存储关闭时，合法目标返回 503，越权或不可见目标仍返回 404。

授权只保证查询时刻的状态。查询与签名之间可能发生编辑或下架；已签发链接是持有者可使用的临时凭证，后续账号禁用、编辑撤下、退出或审核完成不能立即撤销。过期约束新请求，不能中断已经开始的下载，也不能撤回已保存的内容。需要即时撤销时必须另做媒体网关。[上下架/封禁管理接口](publication-moderation-api.md) 已在后续模块接入，包含真实用例操作后公开查询和新签发拒绝的回归；真实浏览器与存储验证仍待完成。

## 存储配置

媒体签名默认关闭，与上传开关独立。已有私有 MinIO 桶要求见 [上传存储配置](upload-storage-setup.md)。在运行后端的终端配置 `.env.example` 对应变量，Compose 的 `.env` 不会自动注入本地 Java 进程。

| 环境变量 | 默认值 | 要求 |
| --- | --- | --- |
| MEDIA_ENABLED | false | true 时启用签名器及启动桶校验；Worker profile 不启用 |
| MEDIA_PUBLIC_ENDPOINT | 空 | 浏览器可达的 HTTP(S) origin，例如 `https://media.example.com` |
| STORAGE_REGION | us-east-1 | 必须与存储端一致，显式指定以避免签名时查询地域 |
| MEDIA_LINK_SECONDS | 60 | 范围 15～120 秒 |
| STORAGE_ENDPOINT | 空 | 后端可达的存储 API 端点，用于启动检查及存储操作 |
| STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY | 空 | 专用应用账号，不能提交真实值 |
| STORAGE_BUCKET | creator-private | 与上传、Worker 指向同一个私有桶 |

公开端点不得含用户名、密码、路径前缀、查询或 fragment。浏览器和后端可以使用不同域名，但必须到达同一存储服务、桶、地域和凭据体系。先对最终域名签名，不能签完再替换域名或端口；反向代理必须保持签名的 Host 和路径语义，不能改变签名查询参数。生产页面与媒体入口均使用 HTTPS。

成功响应为 `{url,expiresAt}`，并设置 `Cache-Control: no-store` 与 `Pragma: no-cache`。链接使用 SDK 原生 GET 签名，约束对象、期限和覆盖响应头：SOURCE 为 video/mp4、COVER 为 image/jpeg、inline、private/no-store。显式地域使签名本身无需逐对象 HEAD 或网络调用。返回的签名 URL 必然含对象路径和访问密钥标识，不能宣称路径隐藏；它不包含 secretKey，但完整 URL 按凭证保护，不写访问日志、错误报告或长期缓存。相关值对象的 toString 已脱敏。

播放器应在实际使用前申请链接，拖动或重新加载遇到过期时重新走授权接口；不得无界重试。浏览器跨域 fetch、带 crossOrigin 的媒体访问和读取 Content-Range 等响应头，需要在最终媒体入口配置限定页面 origin 的 CORS，并验证 Range、代理和浏览器行为。当前未自动配置存储 CORS，也未完成真实浏览器播放验收。

## 验证与运行

PublicMediaIntegrationTest 覆盖公开状态、资源缺失/旧代次、字面搜索与分页、分类停用、作者与管理员边界、待审版本/轮次、编辑撤下、账号失效、CSRF/no-store、签名失败与关闭时的权限顺序。MinioMediaSignerTest 使用真实 SDK 本地签名，检查最终域名、期限、签名参数、响应头和端点拒绝规则，不代替真实存储验证。

真实 MinIO 测试仅在显式设置 `CREATOR_MINIO_IT_PORT` 后运行。必须使用本机独立测试实例，账号 creator_test、密码 integration-only-password；测试创建随机 creator-media-it-* 桶，上传测试对象并在 finally 删除对象及桶，不得指向业务服务或使用生产凭据。

```powershell
$env:CREATOR_MINIO_IT_PORT = '9002'
mvn -s maven-settings.xml '-Dmaven.repo.local=../.local/m2' '-Dtest=MinioMediaIntegrationTest' test
```

该测试包含生产存储适配器写入/下载、私有桶校验、真实签名 GET、206 Range、JPEG 响应头、匿名拒绝、签名/Host 篡改及过期拒绝。样本字节只验证 HTTP 传输，不表示视频可解码。未设置变量时明确跳过，不能算作通过。

2026-11-18 已使用本地 MinIO RELEASE.2025-04-22T22-12-26Z 测试实例运行真实私有桶、签名、Range 和匿名拒绝测试并通过。该源码标签 LICENSE 为 GNU AGPL v3，SDK 8.5.17 为 Apache 2.0；服务端标签仅为测试候选，正式部署还需确认维护状态、镜像来源、许可适用与老师要求。MinIO 不接受仅 AbortIncompleteMultipartUpload 的生命周期规则，清理模式见 [上传接入说明](upload-storage-setup.md)。

最终 MySQL 8.4.3 + RabbitMQ 4.0.5 + FFmpeg 9.0.2 全量回归 147 项：146 通过、1 项真实 MinIO 测试跳过、零失败/错误。新增数据库/授权集成测试 12 项与 SDK 签名 2 项通过；V8 实际迁移通过。运行使用 JDK 23 编译到 Java 21。

2026-11-18 实测：JDK 21 下 MySQL 8.4.3、RabbitMQ 4.0.5、FFmpeg 6.1.1 和 MinIO 测试镜像全量 190 项通过，0 跳过；真实 MinIO 私有桶、上传、签名和 Range 测试通过。正式存储环境、浏览器播放、代理/CORS 和容量仍未验收。

正式存储环境、最终反向代理/CORS、浏览器播放与拖动和生产容量仍待验证；JDK 21 测试环境已通过全量回归，前端页面已接入。[收藏后端](favorite-api.md) 已在后续模块接入，列表批量复用同一公开条件，不以收藏关系替代播放授权。
