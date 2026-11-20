---
name: 帧间 Frame Room
description: 安静的放映厅——暖纸底、松绿墙、陶土色场灯，克制到近乎无声的视频创作者平台界面。
colors:
  frame-pine: "#245e52"
  pine-shadow: "color-mix(in srgb, #245e52 78%, #000)"
  pine-mist: "color-mix(in srgb, #245e52 8%, #fff)"
  projector-terracotta: "#c65b3d"
  title-brass: "#d7b57d"
  screen-blush: "#f0a096"
  on-primary-cream: "#f7f4ec"
  paper-cream: "#f4f3ed"
  white-screen: "#ffffff"
  soft-paper: "#f8f7f1"
  warm-hover: "#f7f4ed"
  ink-pine: "#182522"
  deep-ink: "#233630"
  moss: "#40544a"
  mist-grey: "#748277"
  mist-grey-light: "#87918a"
  mist-grey-mid: "#7c8680"
  hairline: "#d9ddd5"
  hairline-soft: "#e5e8e2"
  control-border: "#ccd8cd"
  disabled-fill: "#f5f7f3"
  error-vermilion: "#b94235"
  warning-ochre: "#b7791f"
  cover-dark: "#213831"
  placeholder-grey: "#dfe2db"
  search-border: "#aeb8b0"
  skeleton-grey: "#f1f0ea"
  skeleton-light: "#faf9f4"
typography:
  display:
    fontFamily: "Microsoft YaHei, PingFang SC, sans-serif"
    fontSize: "34px"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Microsoft YaHei, PingFang SC, sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "0em"
  title:
    fontFamily: "Microsoft YaHei, PingFang SC, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: "0em"
  body:
    fontFamily: "Microsoft YaHei, PingFang SC, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.8
    letterSpacing: "0em"
  label:
    fontFamily: "Microsoft YaHei, PingFang SC, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.6
    letterSpacing: "0.08em"
rounded:
  micro: "2px"
  control: "3px"
  card: "4px"
  pill: "999px"
spacing:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "22px"
  panel: "26px"
  page: "28px"
components:
  button-primary:
    backgroundColor: "{colors.frame-pine}"
    textColor: "{colors.on-primary-cream}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
    height: "32px"
  button-primary-hover:
    backgroundColor: "{colors.pine-shadow}"
    textColor: "{colors.on-primary-cream}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
    height: "32px"
  button-default:
    backgroundColor: "{colors.white-screen}"
    textColor: "{colors.moss}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
    height: "32px"
  button-danger:
    backgroundColor: "{colors.error-vermilion}"
    textColor: "{colors.white-screen}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
    height: "32px"
  filter-chip:
    backgroundColor: "{colors.white-screen}"
    textColor: "{colors.moss}"
    rounded: "{rounded.pill}"
    padding: "6px 14px"
    height: "30px"
  filter-chip-active:
    backgroundColor: "{colors.frame-pine}"
    textColor: "{colors.white-screen}"
    rounded: "{rounded.pill}"
    padding: "6px 14px"
    height: "30px"
  studio-panel:
    backgroundColor: "{colors.white-screen}"
    textColor: "{colors.ink-pine}"
    rounded: "{rounded.card}"
    padding: "26px"
  video-card:
    backgroundColor: "{colors.white-screen}"
    textColor: "{colors.ink-pine}"
    rounded: "{rounded.card}"
    padding: "20px 22px"
  text-input:
    backgroundColor: "{colors.white-screen}"
    textColor: "{colors.deep-ink}"
    rounded: "{rounded.control}"
    padding: "11px 12px"
---

# Design System: 帧间 Frame Room

## Overview

**Creative North Star: "安静的放映厅"**

帧间的界面像一间安静的放映厅：外面是暖色相纸一样的墙，里面是深松绿的放映空间。大面积留给作品本身——封面、标题、时长、作者；界面元素退到发丝般的边框和 11–13px 的说明文字里，几乎不出声。所有结构靠 1px 边框、留白和字重建立，而不是靠阴影、玻璃或渐变。

色彩是低饱和的自然色：松绿承担品牌与主操作，陶土色像场灯一样只点在小标签、链接和「下一步」上，米白纸面承担全站底色。暗色只出现在三种场合：全屏播放流、视频播放器、登录页左栏。它们共同构成「从纸面走进放映厅」的转场，而不是全站主题。

密度是舒展的：面板 26px 内边距，卡片之间 22px，正文 14px/1.8。中文正文不拉开字距；字距只属于全大写英文眉标和封面小注。层级靠 400/600 两级字重完成，500 只留给登录页大标题，700 只给品牌字标与当前导航。

**Key Characteristics:**
- 暖纸底（#f4f3ed）+ 松绿主色（#245e52）+ 陶土点缀（#c65b3d）的三色骨架
- 默认全平：1px 发丝边框，无静态阴影，只在 hover 时抬起
- 4px 面板 / 3px 控件 / 2px 标签 / 999px 胶囊的严格四档圆角
- 中文系统字体栈，无 Web 字体；负字距只用于大标题，正字距只用于大写英文标签
- 暗色界面只服务观看与登录叙事，不作全局主题

## Colors

低饱和的「取景器」配色：纸、松针、陶土、铜金，全部带灰，没有一个纯色。

### Primary
- **帧间松绿 Frame Pine** (#245e52): 主操作、链接、当前导航胶囊、选中标记与焦点色。全站唯一的高识别度颜色。
- **松绿深影 Pine Shadow** (color-mix 78% 松绿 + 黑): 主按钮 hover、登录页左栏底色、深色底上的强调。
- **松绿雾化 Pine Mist** (color-mix 8% 松绿 + 白): 当前导航胶囊底色、状态说明条、头像占位底。
- **放映机陶土 Projector Terracotta** (#c65b3d): 眉标（eyebrow）、封面上的分类、卡片「下一步」、队列选中左侧 3px 竖线、发布按钮。
- **片头铜金 Title Brass** (#d7b57d): 仅登录页左栏的眉标与胶片图形，不进入主站。
- **场灯腮红 Screen Blush** (#f0a096): 仅暗色界面里的已赞状态、分类文字与行内链接。
- **米白字 On-Primary Cream** (#f7f4ec): 松绿与暗色底上的文字，不用纯白。

### Neutral
- **相纸米白 Paper Cream** (#f4f3ed): 全站页面底色。
- **白幕 White Screen** (#ffffff): 面板、卡片、输入框、表格底。
- **米白浅层 Soft Paper** (#f8f7f1): 顶栏背景、登录表单栏、禁用前的浅层容器。
- **悬停暖白 Warm Hover** (#f7f4ed): 列表行、队列项 hover。
- **墨绿黑 Ink Pine** (#182522): 正文与标题。全站不用纯黑。
- **深墨绿 Deep Ink** (#233630): 表单输入值、强调正文。
- **苔绿 Secondary Moss** (#40544a): 次级文字、次要按钮文字、表格说明。
- **雾松灰 Mist Grey** (#748277): 说明文字、字段标签、统计单位。
- **雾松浅 Grey Light** (#87918a): 时间戳、卡片编号、极小注脚。
- **雾松中 Grey Mid** (#7c8680): `.muted` 段落、空状态正文。
- **发丝边框 Hairline** (#d9ddd5): 面板/卡片外框、表格横线、区块分隔。
- **发丝浅 Hairline Soft** (#e5e8e2): 卡片内部横线、历史条目分隔。
- **控件边框 Control Border** (#ccd8cd): 输入框、下拉、文本域、胶囊筛选的描边。
- **禁用底 Disabled Fill** (#f5f7f3): 禁用控件底色。
- **错误朱 Error Vermilion** (#b94235): 错误提示、危险操作。
- **警告赭 Warning Ochre** (#b7791f): 待处理、未读、审核中状态。
- **封面暗场 Cover Dark** (#213831): 视频封面底、全屏播放流外壳。
- **占位灰 Placeholder Grey** (#dfe2db): 封面缺失时的占位。
- **搜索边框 Search Border** (#aeb8b0): 搜索框下边框。
- **骨架灰 Skeleton** (#f1f0ea) / **骨架浅 Skeleton Light** (#faf9f4): 加载骨架的明暗两端。

### Named Rules
**The One Accent Rule.** 陶土色在一屏内的覆盖不超过 5%，只出现在文字与小面积标记上；铜金只属于登录页左栏。主色松绿承担所有可点击的主路径，但也不铺满整屏。

**The Paper Never Pure Rule.** 页面底色永远是 #f4f3ed 的相纸米白；白色只出现在需要与纸面分离的容器上。纯黑 #000 与纯白 #fff 不得作为文字色。

## Typography

**Display Font:** Microsoft YaHei（回退 PingFang SC, sans-serif）
**Body Font:** Microsoft YaHei（同上，同一字族承担全部层级）
**Label/Mono Font:** 无独立标签字体；大写英文标签复用同一栈

**Character:** 单一中文系统字栈，靠字重与字号建立秩序，不借助 Web 字体。界面因此稳定、加载零成本，也决定了标题必须靠 600 字重与负字距收紧，而不是靠大字号衬线制造气势。

### Hierarchy
- **Display** (600, 34px / 1.25, -0.03em): 工作台与后台页面的 h1（`.studio-heading h1`）。登录页左栏标题例外，使用 clamp(32px, 3.4vw, 52px)、500 字重、1.5 行高与 +1px 字距。
- **Headline** (600, 26px / 1.3): 详情页主标题、审核详情标题。
- **Title** (600, 20px / 1.35): 作品卡片标题；面板内 h2 收到 18px。
- **Body** (400, 14px / 1.8): 正文与表单说明；长文（简介、申诉理由）行高 1.9；登录页说明文案最大宽度 350px，作者简介 640px。
- **Label** (500, 12px / 1.6, 0.08em): 字段标签、统计单位、徽标；全大写英文眉标用 12px / +3px 字距，管理导航分组用 11px / +0.14em。
- **Micro** (400, 11px / 1.5): 时间、卡片编号、封面角标；绝对下限 10px，只用于封面装饰字。

### Named Rules
**The Two-Weight Rule.** 层级只用 400 与 600；500 仅限登录页标题，700 仅限品牌字标（`.brand`、`.feed-brand`）与当前导航项。不要在一屏内混用四种以上字重。

**The Chinese Tracking Rule.** 中文字正文一律 0 字距。正字距只给全大写拉丁眉标、封面小注与数字编号；负字距只给 26px 以上标题。

## Layout

**页面骨架。** 内容页面（我的作品、通知、后台）由 `WorkspaceShell` 提供：顶栏 24px 上下 / 6% 左右内边距，下方主区最大宽度 1180px、居中、28px 双侧留白、44px 顶距。公共发现页（`/`）用 1100px 容器；表单页窄到 720px；个人资料页的侧栏固定 180px + 32px 间距。

**栅格。** 公共视频网格四列（≥1280px），1279px 降到三列，1023px 两列，599px 单列。创作者作品卡片两列，760px 单列。后台在 900px 以上用 `minmax(260px, .8fr)` + `minmax(0, 1.6fr)` 的左右队列/详情布局，以下折叠为单列。指标卡四列 → 900px 两列 → 560px 单列。

**节奏。** 卡片间距 22px，面板内边距 26px，面板之间 22px，页面留白 28px，密集控件组 12px。间距不走 4px 网格，而是 8 / 12 / 16 / 22 / 26 / 28 这一组复用值；新布局优先从这组里取。

**响应式断点。** 1279 / 1023 / 900 / 760 / 700 / 599px 六档，全部写在页面级媒体查询里；不引入容器查询或 JS 断点。

**触控。** 桌面控件高 32px；移动端导航与主要链接最小 40px 触控高度，导航改为单行横向滚动并隐藏滚动条。

## Elevation & Depth

**默认全平。** 卡片、面板、表格、导航在静止状态只有 1px 发丝边框与底色差异，没有任何阴影。深度只在两种情况下出现：指针悬停时的整体抬起，以及漂浮在纸面上的搜索框。暗色界面用遮罩渐变建立前后关系，而不是阴影。

### Shadow Vocabulary
- **悬停抬起** (`box-shadow: 0 10px 25px rgba(28,40,34,.08)`): 作品卡片 hover，配合 `translateY(-2px)`，200ms ease。
- **悬停加深** (`box-shadow: 0 12px 26px rgba(28,40,34,.12)`): 公共封面卡 hover，仅封面层。
- **浮层搜索** (`box-shadow: 0 8px 22px rgba(28,40,34,.05)`): 发现页胶囊搜索框，常驻但极轻。
- **选中内嵌** (`box-shadow: inset 3px 0 #245e52`): 后台队列选中项，替代整行填色。
- **印刷错位** (`box-shadow: 16px 13px 0 #24483f`): 仅登录页胶片图形的硬边偏移，不是模糊阴影。
- **骨架脉冲** (`animation: pulse 1.4s infinite`): 骨架屏的横向扫光，不是阴影。

### Named Rules
**The Flat-At-Rest Rule.** 静止表面不允许出现模糊阴影。阴影只能是 hover、浮层或选中状态的响应，且模糊半径不小于 10px。

**The No Glass Rule.** 不使用 backdrop-filter、毛玻璃、发光边框或多层阴影堆叠。暗色沉浸界面的层次靠 `linear-gradient` 遮罩完成。

## Shapes

四档圆角，各司其职：**2px** 给状态标签，**3px** 给输入框、文本域、下拉与视频播放器，**4px** 给面板、卡片、指标卡、公共封面与骨架块，**999px** 给胶囊式筛选、导航项、搜索框、发布入口与提示条。正圆（50%）只给播放按钮、头像、操作轨道按钮。

边框语言是 1px 发丝线：外框 #d9ddd5，内部分隔 #e5e8e2，控件描边 #ccd8cd。强调用 3px 宽的单边竖线（左侧选中、状态说明条），不用四边加粗或彩色外框。

作品封面与视频固定 16:9，封面图 `object-fit: cover`，内嵌 120° 暗角渐变承托白色标题。唯一的非矩形几何是登录页左栏那枚旋转 -7° 的胶片框，它属于叙事插画，不进入产品界面。

## Components

### Buttons
- **Shape:** 3px 圆角（`rounded.control`），高 32px，左右 15px；登录提交为全宽 46px。
- **Primary:** 松绿底 + 米白字，用于提交、发布、确认类主动作。hover 变为松绿深影，无位移。
- **Default:** 白底 + 苔绿字 + #d9ddd5 描边，用于刷新、翻页、取消、次级动作。
- **Danger:** 错误朱底 + 白字，仅用于删除、驳回、移除。
- **Disabled / Loading:** 禁用用 #f5f7f3 底 + 雾松灰字；加载保留宽度并显示原生 spinner，不用骨架替换按钮。
- **Element Plus 映射:** 全站只注册 `ElButton`、`ElInput`、`ElAlert`、`ElTag`。主色通过 `--el-color-primary` 映射到松绿及其 3/5/7/9 混色，禁止出现 Element 默认蓝。

### Chips
- **Style:** 999px 胶囊，白底、#ccd8cd 描边、12px / 600 苔绿字，内边距 5–6px 12–14px（发现页搜索框内为 8px 10px 8px 18px）。
- **State:** 选中即松绿填充 + 白字；计数用 11px 雾松浅，选中时继承同色并降到 75% 不透明度。分类、标签、收藏夹、排序共用同一套胶囊。

### Cards / Containers
- **Corner Style:** 4px。
- **Background:** 白幕 #ffffff；页面为相纸米白 #f4f3ed。
- **Border:** 1px #d9ddd5（内部横线 #e5e8e2）。
- **Shadow Strategy:** 无静态阴影；hover 时 `translateY(-2px)` + 10px/25px 阴影。
- **Internal Padding:** 面板 26px；作品卡片正文 20px 22px 22px；状态标签圆角收到 2px。
- **Cover:** 16:9，深色底 #213831 + 暗角渐变；编号 42px / 600 / -0.08em，角标 10px / +0.18em。

### Inputs / Fields
- **Style:** 3px 圆角，白底，#ccd8cd 描边，11px 12px 内边距，14px 字；标签 14px、上方 18px 间距。
- **Focus:** 原生控件用 2px #d88970 外描边、1px 偏移；Element Plus 输入沿用其内阴影焦点，但主色已映射为松绿。
- **Error / Disabled:** 错误文字用 #b94235，14px / 1.8；禁用底 #f5f7f3 + 雾松灰字；文本域允许纵向拉伸。

### Navigation
- **Header:** 米白浅层底，1px 下边框，24px 上下 / 6% 左右内边距；品牌字标 30px / 700 / +2px，副标 10px / +2px，用竖线分隔。
- **Main Nav:** 14px，条目为 999px 胶囊（6px 10px），当前项松绿雾化底 + 松绿字 + 700；移动端单行横滚，条目最小 40px 高。
- **Admin Nav:** 独立一条 13px 导航，白底上下发丝线；分组标签 11px / +0.14em / 雾松浅，当前项陶土色 + 700。
- **Footer:** 12px / +0.08em / 雾松浅，居中，上边框分隔。

### Signature: 全屏播放流
发现页是唯一的沉浸式界面：100dvh 滚动吸附，深色 #0f1a17 画布，封面模糊放大作背景（28px blur、38% 不透明度），上下双段暗角渐变。右侧 52px 正圆操作轨道，图标 22px、说明 10px；底部 2px 陶土进度条；顶部胶囊分类标签在 16% 米白底与 22% 描边之间切换。桌面端限制在 520px 宽并加左右发丝线，模拟竖屏放映窗口。

## Do's and Don'ts

### Do:
- **Do** 让每个面板、卡片、表格在静止时只有 1px #d9ddd5 边框，深度交给内容。
- **Do** 面板用 4px、控件用 3px、标签用 2px、筛选与导航用 999px，四档之外不新增圆角。
- **Do** 把陶土色限制在眉标、封面分类、链接与「下一步」等 12–13px 的文字上。
- **Do** 正文保持 14px / 1.8，说明文字降到 12–13px 并用雾松灰系。
- **Do** 用 22px 卡片间距、26px 面板内边距、28px 页面留白组织页面。
- **Do** 把暗色（#213831 / #0f1a17 / #17231f）只用于全屏播放、播放器与登录页左栏。
- **Do** 沿用 Element Plus 的交互与可访问性默认值，只通过 CSS 变量改主色和几何。
- **Do** 中文界面配全大写英文眉标（`FRAME ROOM / INBOX`）建立编辑感。

### Don't:
- **Don't** 给卡片或面板加常驻阴影、发光、毛玻璃或多层 elevation。
- **Don't** 使用超过 4px 的卡片/输入框圆角；胶囊只用于短标签与导航，不用于内容容器。
- **Don't** 引入衬线字体、装饰性 Web 字体或第二套中文字族。
- **Don't** 使用纯黑 #000、纯白 #fff 作为正文或标题色，也不要用未带灰的饱和色。
- **Don't** 把陶土色、铜金色或腮红色铺成大色块、按钮底或整页背景。
- **Don't** 用装饰性渐变；渐变只允许出现在封面暗角、播放流遮罩与骨架扫光里。
- **Don't** 使用 Element Plus 默认蓝或自定义第二主色；所有主色引用必须落在松绿体系内。
- **Don't** 给中文正文加字距，或把 26px 以下标题的字距拉到 ±0.03em 之外。
- **Don't** 在 10px 以下排文字；封面装饰字是唯一例外。
- **Don't** 在浅色页面里混入暗色区块制造「对比感」，暗色是观看场景的专属。
