# HeyTap 浏览器增强与搜索引擎管理 (COUI 2.0)

基于 Xposed / LSPosed 与 **ColorOS 17 (COUI 1.1.0)** 设计语言的 OPPO / OnePlus / Realme（HeyTap 浏览器 `com.heytap.browser`）增强与净化模块。

## 核心功能

### 1. 搜索引擎与主页导航管理
- **搜索引擎管理**：自动捕获浏览器内置引擎配置，支持修改名称/URL、新增自定义搜索引擎、冲突识别与更新合并。
- **热门网站管理**：自定义简洁主页宫格快捷网站，支持自定义名称、URL 与图标地址。
- **搜索热词控制**：一键禁用搜索栏底纹滚动热词与运营推荐词。
- **统一拖拽排序**：内置引擎、自定义引擎与热门网站均支持「编辑/完成」切换与三横线把手实时拖拽排序（`Spring.DampingRatioLowBouncy` 弹簧阻尼缩放与阴影抬升）。

### 2. 浏览器全方位净化（可逐项独立开关）
- **移除开屏广告**：跳过 `StepSplash` / `SplashConsumer` / `SplashFeedAdImpl` 在线开屏请求。
- **离线错误页彻底净化**：通过 `ErrorPageAdHook` 四层源头封堵（`HtmlPageManagerImpl` 模板样式注入、`BrowserIFlowJsObject` 配置拦截、`BrowserErrorJsObject` 状态桥封堵、底层门闸），彻底清除断网/错误页底部的「你可能想搜 / 为你推荐 / 百度搜索结果」。
- **关闭资讯流与热榜**：关闭底层 `iflow`、热榜与首页推荐流。
- **强制简洁主页**：锁定简洁主页模式。
- **移除搜索框 App 推广**：过滤 `SugMultiAdapter` 中的 `sugNaturalApp` / `sugAd` 推广项。
- **移除下载页与弹窗广告**：隐藏下载列表底部推荐位与下载确认弹窗广告。
- **解除网页浏览限制**：放行 `UrlBlocker` 网址拦截与安全警告弹窗。
- **移除窗口数量限制**：多标签页上限提升至 `999`。
- **关闭营销推送渠道**：精准静音 `browser_private_letter` 等运营通知渠道，保留下载等正常通知。
- **过滤云控引擎配置**：阻止云端下发覆盖本地搜索引擎配置。

### 3. ColorOS 17 (COUI) 原生界面体验
- 全面迁移至 [COUI 1.1.0](https://github.com/suqi8/coui)（Compose Multiplatform），支持 `MonetSystem` 壁纸动态取色与深浅色自适应。
- 采用 COUI 原生 `BasicComponent`、`CardListPosition`、`COUIIcons.Basic.ArrowRight` 与底部滑入式 `OverlayDialog`。

## 构建说明

```bash
./gradlew :app:assembleDebug
```

> 若在 ARM64 Linux 容器或本机交叉环境下编译，可通过 `-Pandroid.aapt2FromMavenOverride=/path/to/aapt2` 指定本机 `aapt2` 二进制。

## 致谢

- 原项目基础：[Asstiff/HeytapBrowserSearchEngineManagerXposedModule](https://github.com/Asstiff/HeytapBrowserSearchEngineManagerXposedModule)
- UI 组件库：[suqi8/coui](https://github.com/suqi8/coui)
