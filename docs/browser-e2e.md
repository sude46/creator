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
