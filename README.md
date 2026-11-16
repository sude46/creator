# 映作视频创作者平台

基于 Vue 3、Spring Boot 3 的 Java Web 课程项目。用户上传自己的视频，平台负责处理、审核与播放，不生成视频。

## 当前进度

第一阶段是账号与权限：注册、Session 登录、退出、CSRF 防护、普通用户与管理员鉴权、禁用账号会话失效、管理员初始化，以及登录注册页面。

注册和登录目前按来源 IP 进行单实例限流，每分钟合计最多 20 次，不信任客户端提供的转发 IP。开发代理或生产 Nginx 下可能共享代理 IP 配额；正式部署需配置受信代理边界或网关限流，多实例时需要共享限流状态。

视频上传、媒体处理、审核、播放、搜索与收藏尚未实现。首页当前为账号模块的阶段页面，不包含模拟视频数据。

## 工程结构

```text
backend/      Spring Boot API，按业务模块组织
frontend/     Vue 3 + TypeScript + Element Plus
deploy/       本地基础设施配置
```

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

MySQL 使用本机端口 3307，RabbitMQ 使用 5672，管理页面使用 15672。端口仅绑定 127.0.0.1。RabbitMQ 当前仅为后续模块预备，账号模块不依赖它；只启动数据库可在命令末尾加 `mysql`。

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

## Git 与双仓库

每完成一个模块，先验证再独立提交，并分别推送 Gitee 和 GitHub。当前设计文档 `docs/视频创作者平台需求分析与架构设计.md` 按要求不提交，其他必要工程文档正常管理。`.env`、密钥、视频、构建产物与本地依赖缓存均忽略。

仓库地址确认后添加两个远程，不把密码或 token 写进地址：

```powershell
git remote add gitee <你的Gitee仓库地址>
git remote add github <你的GitHub仓库地址>
git push -u gitee main
git push -u github main
```

后续每个模块分别执行两次 push；两端可能单独失败，应分别核对结果后重试，不用强制推送掩盖差异。推荐远程仓库初始为空；若已有 README 或历史，先获取并检查，不直接覆盖。
