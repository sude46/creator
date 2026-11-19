# 部署资产与本轮验证

## 新增资产

- `backend/Dockerfile`：Maven 多阶段构建，运行阶段 JDK 21 JRE、非 root 用户 `10001`、包含 FFmpeg/FFprobe。
- `frontend/Dockerfile`：Node 22 构建，Nginx 静态托管。
- `frontend/nginx/default.conf`：SPA、`/api` 反代、220 MiB 请求体上限、基础安全响应头。
- `deploy/compose.prod.yml`：MySQL、RabbitMQ、API、Worker、Web；API 和 Worker 只读根文件系统、`cap_drop: ALL`、`no-new-privileges`、PID/内存/CPU 限制。
- `deploy/nginx/tls.conf.example`：外部 TLS 终止示例，包含 HSTS 和安全头。
- `deploy/nginx/http-8080.conf.example`：备案前 8080 端口 HTTP 演示示例，保留现有 80/443 站点，并把签名媒体路径反代到 MinIO。
- `deploy/check-minio.sh`：检查 MinIO `api stale_uploads_expiry` 不超过 24h。
- `.github/workflows/ci.yml`：JDK 21 后端 H2 回归、前端生产构建、生产依赖审计、Compose 配置校验。

## 备案前 8080 演示

大陆机房未备案域名不能使用 80/443。临时演示将宿主机 Nginx 放在 8080，Web 容器通过 `WEB_PORT=8081` 只监听 `127.0.0.1:8081`；示例见 `deploy/nginx/http-8080.conf.example`。`MEDIA_PUBLIC_ENDPOINT` 必须与浏览器访问的公网源一致，媒体签名 URL 才能通过同源 Nginx 访问。2026-11-19 已在主机上以 `creator.llllzzzjjjj.ltd` 的 Host 头完成登录、管理页面、封面签名与媒体读取验证；公网 DNS 当时仍为 NXDOMAIN，需先为所有域名添加指向主机公网 IP 的 A 记录。该模式没有 TLS，不能作为生产验收。

## 演示数据与重置

`deploy/seed-demo.py` 使用标准库、HTTP API 和 FFmpeg 生成合成演示作品，随后真实上传、等待转码、提交审核并由管理员批准；只保留指定数量的作品待审。`--reset` 仅删除标题以 `[演示]` 开头的作品，不会删除真实作者内容。脚本从环境变量读取账号密码，不把凭据写入仓库。2026-11-19 在本机 8080 演示环境实际生成 12 部作品、4 个分类，11 部通过、1 部保留待审；首页分类筛选、相关推荐和审核预览已浏览器验证。

## 腾讯 COS 验收

2026-11-19 将演示存储从 MinIO 测试镜像切换到腾讯云 COS：

- 桶：私有桶，地域 `ap-guangzhou`
- provider：`STORAGE_PROVIDER=cos`，原生 `cos_api:5.6.247`
- 媒体：COS 直接签名 URL，`MEDIA_PUBLIC_ENDPOINT` 等于桶域名
- 生命周期：`sources/` 前缀一天期未完成分片中止规则
- CORS：允许应用来源的 GET/HEAD
- 迁移：MinIO 中 29 个已有对象复制到 COS，保留原对象键

实测：

- `HeadBucket`、版本控制读取、生命周期读取、对象 PUT/GET/DELETE 通过
- 预签名 GET 返回，Range `206`，匿名访问 `403`
- API 启动时 COS 私有桶探测通过
- Web 封面从 COS 加载；播放器从 COS 播放，Firefox `readyState=4`
- 新建作品上传到 COS，Worker 从 COS 下载、生成封面并上传回 COS；作品达到待审核状态后可作者预览
- 删除作品后 `video_deletion.state=DONE`，对象清理使用 COS provider
- 全量真实依赖回归 206 项、0 失败/错误/跳过（包含真实 COS 集成测试）

边界：未做跨地域复制、COS 正式容量/配额压测、CDN、生命周期规则变更自动化和密钥轮换演练。

## 2026-11-18 验证结果

### 镜像构建

```text
docker build -t creator-backend:e2e backend   -> success
docker build -t creator-frontend:e2e frontend -> success
```

### 生产型 Compose

`docker compose -p creator-prod ... up -d` 实际启动：

- MySQL 8.4.3：healthy
- RabbitMQ 4.0.5：healthy
- API：healthy，`GET /api/auth/csrf` 200
- Worker：启动并连接数据库、RabbitMQ、MinIO
- Web：`GET /` 200，`GET /api/auth/csrf` 200

容器检查：

```text
api:    uid=10001(app), readonly=true, capdrop=[ALL], pids=256
worker: readonly=true, capdrop=[ALL], pids=128
```

API 和 Worker 的根文件系统只读；Worker 的 `/work` 和 `/tmp` 使用 tmpfs；均使用非 root 用户、`no-new-privileges` 和资源上限。本轮使用 `--network host` 的临时 Worker 沙箱验证了文件系统/权限启动，生产 Compose 使用独立网络。操作系统级网络隔离、磁盘配额和真实节点资源限制仍需在目标主机复核。

### MinIO 清理检查

```text
$ MINIO_ALIAS=probe sh deploy/check-minio.sh
OK: stale_uploads_expiry=24h, stale_uploads_cleanup_interval=6h
```

### 本机负载基线

通过 Nginx TLS，对 200 条已发布种子数据和真实 MySQL 执行：

| 场景 | 请求数/并发 | p50 | p95 | 吞吐 |
| --- | --- | --- | --- | --- |
| 公开列表 | 200 / 20 | 120.39 ms | 318.08 ms | 132.0 req/s |
| 分类列表 | 200 / 20 | 81.34 ms | 117.90 ms | 244.6 req/s |
| 作者作品列表 | 100 / 10 | 44.49 ms | 88.13 ms | 200.6 req/s |
| 媒体封面签名 | 100 / 10 | 39.04 ms | 84.46 ms | 223.8 req/s |

这些是本机 Docker + 自签名 TLS 的基线，不是生产容量结论。MySQL slow query log 阈值 50ms，负载后 `mysql.slow_log` 为 0 条。

### 上传带宽与准入

- 20 MiB 文件经 Nginx TLS 上传：
  - 0.754s，27.8 MiB/s
  - 0.446s，44.8 MiB/s
  - 0.496s，40.3 MiB/s
- 同账号 3 个并发上传：2 个返回 429 `UPLOAD_BUSY`，1 个成功，符合每账号 1 个在途上传的准入规则。
- 这是本机 loopback/磁盘结果，不代表公网带宽或生产代理表现。

### 备份恢复演练

- MySQL：`mysqldump --single-transaction` 备份 140 KiB，恢复到独立 `creator_restore` 库；源库和恢复库均为 206 条视频、2 个账号、0 条收藏，Flyway 版本 10。
- MinIO：`mc mirror` 备份 8 个对象、80.04 MiB，删除后恢复到 `creator-restore` 桶，对象数 8，Flyway 数据库版本 10。
- 备份文件位于 `/tmp/opencode/backup.sql` 和 `/tmp/opencode/minio-backup/`，不进入 Git。

### Flyway

Flyway 从 11.7.2 升级到 13.12.2。MySQL 8.4.3 迁移实际通过，启动日志不再出现“MySQL 8.4 超出已测试版本范围”的警告。升级后全量 206 项真实依赖测试通过。

## 仍需生产环境确认

- 真实公网 TLS、DNS、证书链和 CDN。
- 目标服务器的 CPU/内存/磁盘/网络配额与操作系统沙箱。
- 生产规模数据、真实用户并发、慢查询和上传带宽。
- 离线备份存放、恢复时间目标和真实故障切换。
- 目标 Chrome/Edge/Safari 的浏览器兼容性。
