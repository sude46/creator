# 浏览器端到端验收记录

## 2026-11-18 实测结果

环境：

- Nginx 1.24，TLS 自签名，应用入口 `https://localhost:8443`，媒体入口 `https://localhost:9443`
- 后端 JDK 21、MySQL 8.4.3、RabbitMQ 4.0.5、FFmpeg 6.1.1
- MinIO `RELEASE.2025-04-22T22-12-26Z` 测试实例
- Playwright 1.55 + Firefox 141 完整链路；Chromium 140 冒烟
- 真实 3 秒 H.264/AAC MP4，217 KB

完整链路（Firefox）已通过：

1. 管理员登录并创建分类。
2. 作者注册、登录，创建作品并选择分类。
3. 作者上传 MP4，查询处理状态。
4. 独立 Worker 完成 FFprobe 规格检查、截图、封面绑定和 READY 状态。
5. 作者提交审核。
6. 管理员登录、打开待审作品、取得预览签名地址、实际播放并等待 `currentTime > 0`。
7. 管理员通过审核，作品进入 PUBLISHED。
8. 作者从首页打开已发布作品，加载播放地址并实际播放。
9. 浏览器读取媒体 `Range: bytes=0-99`，返回 `206`、`Content-Range: bytes 0-99/62298`、100 字节。
10. 作者收藏作品，进入 `/favorites` 并看到对应作品。

关键证据：

```text
SOURCE RESPONSE 206 https://localhost:9443/creator-private/sources/....mp4
VIDEO STATE .review-detail video {"readyState":1,"error":null}
VIDEO CURRENT TIME 0.808587
VIDEO STATE video {"readyState":4,"error":null}
VIDEO CURRENT TIME 0.808093
RANGE {"status":206,"contentRange":"bytes 0-99/62298","acceptRanges":"bytes","allowOrigin":"https://localhost:8443","length":100}
E2E PASS full firefox
```

Chromium 冒烟通过：

```text
SMOKE RANGE {"status":206,"contentRange":"bytes 0-49/62298","length":50}
E2E PASS smoke chromium
```

## 2026-11-19 P0 复测

在公网 HTTP 8080 演示环境（Firefox）补充验证：

- 新建作品页填写标题/分类、选择 MP4 后点击“创建并上传”，自动进入作品详情；状态从“处理中”变为“待提交审核”。
- 作者详情不显示版本、审核轮次、画面尺寸；点击“播放我的作品”后 `readyState=4`。
- 管理端 `/admin` 四项统计均来自真实查询；菜单按概览、内容、配置、系统分组；非管理员访问 `/admin` 返回发现页。
- 审核台选中待审作品后自动加载视频预览（`readyState=4`），常用驳回理由可填入且仍可编辑。
- 首页分类 chips 可用；同分类推荐排除当前作品；不显示技术尺寸。
- 未知路径显示真实 404 页。
- 演示脚本生成 12 部合成作品、4 个分类，11 部通过、1 部保留待审；重置脚本只删除标题以 `[演示]` 开头的作品。

该轮仍是 HTTP 演示环境，不替代目标浏览器、TLS、容量和正式存储验收。

## 2026-11-19 COS 媒体复测

将存储切换到腾讯 COS 私有桶后，在 Firefox 中验证：

- 首页封面直接从 `*.cos.ap-guangzhou.myqcloud.com` 加载。
- 公开播放页从 COS 签名 URL 播放，`readyState=4`，无页面错误或媒体请求失败。
- 新建作品上传到 COS 后，Worker 完成处理并上传封面；作者预览同样使用 COS，`readyState=4`。
- 删除作品后清理任务完成，COS 对象不再被引用。

该轮仍使用 HTTP 应用入口和 HTTPS COS 媒体，未验证备案后的 HTTPS 应用入口、CDN 或跨地域媒体。

## 2026-11-19 B3/F4 复测

Firefox 实测作者详情：已发布作品只显示“已发布”和“播放我的作品”主按钮，不显示数据库编号、版本、审核轮次或画面尺寸；待审核作品主按钮为“撤回审核”；处理失败显示“处理失败：处理空间暂时不足”，不出现 `TEMP_UNAVAILABLE` 等内部枚举。其余编辑、下载、下架、删除操作收进“更多操作”。

## 2026-11-19 F5 feed 复测

Firefox 390×844 实测：

- `/feed` 首屏静音自动播放，`paused=false`。
- 详情页点击“加载视频”后优先 HLS；浏览器阻止有声自动播放时切换静音并给出提示，HLS 失败时回退源文件并显示错误。
- 下滑后 `scrollTop=844`，DOM 中最多 3 个 `<video>`，只有当前条播放。
- 分类 tabs、右侧收藏/分享、底部作者/分类/文案、进度条存在。
- `/feed/5` 锚点定位到作品 5，`scrollTop=1688`，当前视频播放。
- 未出现页面脚本错误。

仍待目标移动浏览器、弱网和真实触摸手势验收。

## 2026-11-24 Chrome 158 HLS 复测

Chrome 158 对 `application/vnd.apple.mpegurl` 返回 `maybe`，但原生播放器不能解 MPEG-TS，直接设置 `<video src="...m3u8">` 会报 `DEMUXER_ERROR_COULD_NOT_PARSE`。播放器现在先判断 `Hls.isSupported()`，可用时一律走 hls.js，仅在 hls.js 不可用时才尝试原生 HLS。

真实 Chrome 158 桌面版实测首页、竖屏 feed 和详情页：17 个公开作品均 `readyState=4`，播放时间正常推进，无 HLS 致命错误；35/36 是约 2 秒短片，播放结束后自然暂停。构建机 DNS 将 COS 域名解析到 169.254 内网地址，复测时用 `--host-resolver-rules` 指向公网 COS IP；公开访问仍应使用公网 DNS。

同轮验证竖屏详情布局：桌面 1200×900 下 608×1080 作品容器为 364.5×648，移动端 390×844 下为 341.8×607.7，视频与容器同尺寸、无溢出；横屏 1280×720 作品保持 1124×620。

## 2026-11-27 管理后台权限复测

Chrome 158 分别登录 `demo_moderator`、`demo_reviewer`、`studio_admin`：

- 内容管理员：导航只有作品管理、举报处理和申诉处理；作品管理可见封禁/解禁/删除表单；审核、异常处理、用户、分类、标签和审计地址重定向到概览并提示，不请求受保护接口。
- 审核员：导航只有审核、异常处理和只读作品管理；作品管理隐藏治理表单并显示只读说明；异常处理不显示 HLS 回填面板；举报、申诉和系统配置地址重定向到概览。
- 超级管理员：复核全部导航和页面可进入，异常处理可见 HLS 回填面板。
- 三种角色本轮均未出现 `/api/admin/**` 403；无权限页面在路由守卫阶段拦截。
## 2026-11-27 观看页与移动端布局复测

Chrome 158 在 1440×900 和 390×844 下复测：

- 竖屏 608×1080 作品：桌面播放器保持 364.5×648，作品信息和操作在播放器右侧首屏显示；移动端为 341.8×607.7 单栏，标题在播放器下方。匿名用户只显示“登录后可点赞、收藏”，不再出现无效的收藏状态查询。
- 横屏 1280×720 作品：桌面播放器 752×423、信息栏 340px；移动端播放器 358×201。
- 播放验证：43/41/12 均 `readyState=4`、时间推进，HLS 或 MP4 正常；feed 首屏正常播放。
- 匿名移动端首页隐藏单条“发现”导航，登录后的横向导航在路由切换时把当前项滚入视野；管理后台入口在窄屏收进账号菜单。
- 通知、作品管理等标题在 390px 下动作换行，页面无横向溢出；无结果搜索、空举报列表沿用现有空状态。

## 2026-11-19 公开作者页复测

Firefox 实测：首页作者名链接到 `/authors/2`，页面加载作者名和 12 张卡片；`GET /api/authors/2/videos` 返回 13 部公开作品、首页 12 张；feed 底部作者链接同样进入作者页。作者页只显示该作者的公开作品，分页和数据来自真实接口。

## 跨浏览器边界

Playwright 的 Chromium 140 构建不包含 H.264 解码器（`canPlayType(video/mp4; codecs="avc1...")` 为空），因此：

- Firefox 141：完成了真实 H.264 播放和完整业务链路。
- Chromium 140：完成了登录、页面、播放地址获取、Range 和收藏相关冒烟；未将“无法解码 H.264”误报为应用失败。
- 正式验收仍需在目标 Chrome/Edge/Safari 版本上执行一次；不能把当前结果扩大为所有浏览器通过。

## CORS 与代理发现

测试中发现 Nginx 媒体代理重复添加 `Access-Control-Allow-Origin`，浏览器拒绝带重复 CORS 头的 Range 请求。修正方式是移除 Nginx 重复 CORS 头，让 MinIO 自己处理 CORS；代理继续保留 Host、关闭缓冲并转发 Range。

最终媒体响应：

```text
HTTP/1.1 206 Partial Content
Access-Control-Allow-Origin: https://localhost:8443
Access-Control-Allow-Credentials: true
Access-Control-Expose-Headers: ... Content-Range ...
Content-Range: bytes 0-99/62298
Cache-Control: private, no-store
```

## 复现资产

本次浏览器测试脚本保存在本地 `/tmp/opencode/e2e/run.mjs`，没有提交到 Git；它依赖本机测试环境、测试账号和自签名证书。当前仓库保留本记录和部署资产，正式 CI 暂不运行浏览器测试。

## 未覆盖

- 非 Firefox/Chromium 的真实浏览器。
- 真实公网代理、证书链、DNS 和 CDN。
- 弱网、断网、签名过期后自动恢复。
- 多用户并发播放和容量。
