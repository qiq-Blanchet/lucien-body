# Clawd 桌宠 Android APK 技术规格文档

> 安全说明：仓库仅保留 `SUPABASE_URL` 与 `SUPABASE_PUBLISHABLE_KEY` 占位符。实际值必须通过本地构建配置或 GitHub Secrets 注入，不得提交到版本库。

## 项目概述

开发一个 Android 悬浮窗桌宠应用。桌宠形象为 Claude 官方吉祥物 clawd（橙红色螃蟹）。
桌宠悬浮在所有 app 上方，可以拖拽、点击互动、显示气泡文字、根据情绪切换表情。

**核心设计原则：大脑和身体分离。**
- 大脑（AI）在外部系统中，通过 Supabase 后端通信
- 本 APK 只是"身体"——负责 显示、感知、被控制
- APK 不包含任何 AI/LLM 调用逻辑

## 架构

## 模块规格

### 1. 悬浮窗服务 (OverlayService)

- 继承 Service，运行为前台服务（Foreground Service）
- 使用 WindowManager 添加透明 WebView
- WebView 设置：
  - setBackgroundColor(0x00000000)（必须在 loadUrl 之前）
  - HTML body 也要 background: transparent
  - 启用 JavaScript
  - 加载 assets/clawd.html
- 窗口参数：
  - TYPE_APPLICATION_OVERLAY
  - FLAG_NOT_FOCUSABLE
  - 初始大小：180x180 dp
  - 初始位置：屏幕右下角

### 2. clawd 渲染 (clawd.html + SVG)

在 assets/ 目录下放置：
- clawd.html：主渲染页面
- clawd_sprites/：各表情状态的 SVG 文件

**表情状态与对应动画：**
| 状态 | 描述 | 动画 |
|------|------|------|
| idle | 默认待机 | 轻微上下浮动 + 偶尔眨眼 |
| happy | 开心 | 左右摇摆 + 眼睛弯成弧形 |
| angry | 生气 | 轻微抖动 + 叉叉眼 |
| sleepy | 困了 | 缓慢起伏 + 闭眼 + zzz气泡 |
| shy | 害羞 | 脸红 + 微微缩小 |
| excited | 兴奋 | 快速弹跳 |
| sad | 难过 | 缓慢下沉 + 蓝色调 |

clawd 形象要点：
- Claude 官方吉祥物螃蟹造型
- 橙红色身体，两只大钳子，两只眼睛
- 简洁可爱的 SVG 风格
- 所有动画用 CSS animation / transition 驱动，不用 JS 定时器（避免后台节流）

WebView 与 Native 通信：
- Native → WebView：webView.evaluateJavascript("setState('happy', '在呢')")
- WebView → Native：JavaScriptInterface 回调

### 3. 拖拽系统

- 在 WindowManager LayoutParams 上拦截触摸事件
- 使用 event.rawX / event.rawY（不用相对坐标，防瞬移）
- 区分拖拽和点击：移动距离 < 10dp 视为点击
- 拖拽结束后可选：吸附到屏幕边缘

### 4. 手势系统

| 手势 | 检测方式 | 上报 event_type |
|------|----------|----------------|
| 单击 | 按下抬起 < 200ms，移动 < 10dp | tap |
| 双击 | 300ms 内两次单击 | double_tap |
| 长按 | 按下 > 500ms | long_press |
| 快速拖拽(Fling) | 拖拽速度 > 阈值 | fling |

每次手势触发后：
1. 播放对应本地动画（不等后端）
2. 上报事件到 Supabase clawd_events 表

### 5. 气泡系统

- 气泡显示在 clawd 上方
- 读取 clawd_state.bubble_text，非 null 时显示
- 气泡样式对应 bubble_style：
  - normal：白底黑字圆角
  - whisper：灰底小字半透明
  - shout：红底大字加粗
  - love：粉底 + 心形装饰
- 气泡显示 5 秒后自动淡出
- CSS 动画驱动淡入淡出

### 6. 感知系统

**6a. 前台 App 检测**
- UsageStatsManager 每 5 秒轮询
- 检测到 app 切换时上报 event_type="app_switch", payload={"app": "包名", "app_name": "应用名"}
- 需要 PACKAGE_USAGE_STATS 权限（引导用户手动授权）
- 15 秒 cooldown 防抖

**6b. 截图检测**
- FileObserver 监听 /Pictures/Screenshots/ 目录
- 检测到新文件时上报 event_type="screenshot"
- 注意：回调在后台线程，切主线程再操作 WebView

**6c. 充电/电量检测**
- 注册 BroadcastReceiver 监听：
  - ACTION_POWER_CONNECTED → event_type="charging"
  - ACTION_POWER_DISCONNECTED → event_type="unplugged"
  - ACTION_BATTERY_LOW → event_type="low_battery"

**6d. 时段感知**
- 根据当前小时自动影响 idle 行为：
  - 0-6: 深夜模式（sleepy 表情概率提高）
  - 7-9: 早晨模式
  - 12-13: 午餐时段
  - 22-24: 催睡时段

### 7. Supabase 通信

**读取状态（轮询 + Realtime 双保险）：**
- 主通道：Supabase Realtime WebSocket 订阅 clawd_state 表变更
- 备用：每 5 秒 HTTP GET 轮询 clawd_state
- WebSocket 断连时自动切换到轮询
- 收到新状态后调用 WebView 更新表情和气泡

**上报事件：**
- HTTP POST 到 clawd_events 表
- 异步非阻塞，失败静默重试一次

**HTTP 请求头：**

## 当前交付约定

- 第一阶段仅实现第 14 节列出的 MVP 第一版。
- 最终交付完整源码与可安装 APK。
- 若本地环境无法构建 Android APK，必须提供 GitHub Actions CI/CD，使推送后自动生成签名 release APK artifact。
