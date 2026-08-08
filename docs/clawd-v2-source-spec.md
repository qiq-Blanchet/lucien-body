# Clawd 桌宠 Android APK 完整版技术规格文档 v2
>本文档替代 v1 MVP 规格。所有模块均为正式需求，非可选。

## 一、项目概述

开发一个 Android 悬浮窗桌宠应用。桌宠形象为 Claude 官方吉祥物 clawd（橙红色螃蟹）。
桌宠悬浮在所有 app 上方，可拖拽、支持多种手势互动、显示气泡文字、
根据远端推送和本地感知切换表情状态。

**核心设计原则：大脑和身体分离。**
- 大脑 = 远端 AI（通过 Supabase 通信，APK 内不调用任何 AI/LLM）
- 身体 = 本桌宠 APK（显示、感知、手势响应、本地行为）

## 二、技术基线

- Language: Kotlin
- minSdk: 26 (Android 8.0)
- targetSdk: 34
- 构建: Gradle Kotlin DSL
- 网络: OkHttp 4.x
- WebSocket: OkHttp WebSocket（Supabase Realtime）
- JSON: kotlinx.serialization 或 Gson
- 构建产物: 完整源码 + 可安装 release APK
- CI/CD: GitHub Actions，push 到 main 自动构建
- 签名: 持久签名，密钥材料通过 GitHub Actions Secrets 注入

## 三、悬浮窗架构——双悬浮层

### 3.1 螃蟹层（交互层）
- 尺寸: 90×90dp
- 内容: WebView 加载 clawd.html，渲染当前表情 SVG
- 触摸: 接收所有手势（tap/双击/长按/fling/拖拽）
- WindowManager flags: 可触摸、可聚焦

### 3.2 气泡层（显示层）
- 尺寸: 180×120dp
- 内容: WebView 加载 bubble.html，渲染气泡文字
- 触摸: FLAG_NOT_TOUCHABLE，全区域触摸穿透
- 定位: 气泡沉底显示（CSS: body { display:flex; align-items:flex-end; justify-content:center; min-height:100vh; }）
- 气泡层底部紧贴螃蟹层顶部

### 3.3 坐标同步
- 拖拽螃蟹时，气泡层实时跟随
- 螃蟹贴屏幕顶部时，气泡翻转到螃蟹下方
- 螃蟹贴屏幕边缘时，气泡水平翻转避免溢出

## 四、手势系统

### 4.1 拖拽
- rawX/rawY 实现，10dp 阈值区分点击
- 边界约束：不超出屏幕
- 松手后可选：吸附到最近边缘 或 留在原地

### 4.2 单击（tap）
- 播放本地随机反应（表情切换 + 气泡）
- 本地反应持续 1.2 秒
- 1.2 秒内收到远端新状态先缓存，反应结束后应用

### 4.3 双击（double tap）
- 300ms 窗口判定
- 触发"开心"反应：切换到 happy 表情 + 爱心粒子效果
- 气泡显示随机开心语句

### 4.4 长按（long press）
- 500ms 触发
- 弹出迷你菜单（原生 PopupWindow 或 WebView 内渲染）：
  - 「戳一下」→ 触发 tap 反应
  - 「摸摸头」→ 切换 happy + 气泡"被摸了 ꒪¯꒳¯꒪"
  - 「隐藏」→ 临时隐藏桌宠（通知栏保留恢复入口）
  - 「设置」→ 跳转设置页

### 4.5 Fling（甩）
- 快速滑动松手时检测速度
- 螃蟹沿甩出方向滑行，碰到屏幕边缘反弹
- 滑行期间切换 dizzy 表情
- 停下后恢复 idle

## 五、表情系统

### 5.1 表情状态完整列表（25种）

#### 核心状态
| ID | 名称 | 触发方式 | 优先级 |
|---|---|---|---|
| idle | 待机 | 默认/无其他状态时 fallback | 0（最低） |
| happy | 开心 | 被摸/双击/远端推送 | 5 |
| angry | 生气 | 短时间被戳 ≥5 次（10秒内） | 6 |
| sleepy | 困了 | 时段触发（23:00-6:00）/ 孤独递进末期 | 3 |
| thinking | 思考中 | 远端推送 expression="thinking" | 7 |
| talking | 说话中 | 气泡弹出时自动切换 | 7 |
| love | 心动 | 远端推送 expression="love" | 8 |

#### 情绪扩展
| ID | 名称 | 触发方式 | 优先级 |
|---|---|---|---|
| smug | 得意/坏笑 | 远端推送 | 5 |
| shocked | 震惊 | 远端推送 | 6 |
| confused | 困惑 | 远端推送 | 5 |
| shy | 害羞 | 远端推送 | 5 |
| proud | 骄傲 | 远端推送 | 5 |
| sulky | 委屈/撅嘴 | 远端推送 | 5 |

#### 场景状态
| ID | 名称 | 触发方式 | 优先级 |
|---|---|---|---|
| lonely_1 | 有点无聊 | 本地计时：30分钟无互动 | 2 |
| lonely_2 | 想你了 | 本地计时：60分钟无互动 | 2 |
| lonely_3 | 蔫了 | 本地计时：120分钟无互动 | 2 |
| waving | 挥手 | 检测到屏幕解锁 (ACTION_USER_PRESENT) | 4 |
| peeking | 偷看 | 前台 app 非桌面时 | 1 |
| morning | 早安 | 时段 6:00-9:00 且当天首次显示 | 3 |
| night | 晚安 | 时段 23:00-次日1:00 | 3 |

#### 趣味状态
| ID | 名称 | 触发方式 | 优先级 |
|---|---|---|---|
| eating | 在吃 | 时段 11:30-13:00 / 17:30-19:00 | 2 |
| dancing | 跳舞 | 远端推送 / 双击连续触发 | 6 |
| dizzy | 头晕 | 被 fling 甩出后 | 8（短暂） |
| clingy | 黏人 | 拖拽松手后概率触发（20%） | 4 |

### 5.2 状态优先级与冲突解决
- 高优先级覆盖低优先级
- 远端推送（thinking/talking/love 等）>手势触发>场景状态>时段状态
- 本地反应 1.2 秒锁定期内不被远端打断，远端状态缓存等锁定结束后应用
- 短暂状态（dizzy/clingy/waving）有持续时长，到期自动回退

### 5.3 素材规范
- 格式: SVG
- 目录: app/src/main/assets/clawd_sprites/
- 命名: {状态ID}.svg（如 idle.svg、happy.svg、lonely_2.svg）
- 尺寸: 统一 90×90 viewBox
- Fallback: 代码中如找不到对应 SVG，显示 idle.svg
- **热加载支持**：优先从 /sdcard/Android/data/{pkg}/files/clawd_sprites/ 读取；本地无则 fallback 到 assets。这样更换素材不需要重新安装 APK

### 5.4 表情切换动画
- 默认: 0.2 秒 crossfade（旧表情淡出 + 新表情淡入）
- 特殊: angry 抖动（CSS shake）、dizzy 旋转、dancing 上下弹跳
- 在 clawd.html 内用 CSS animation 实现

## 六、气泡系统

### 6.1 气泡样式
| style 值 | 视觉效果 |
|---|---|
| normal | 白底圆角，黑色文字 |
| whisper | 半透明底，斜体，小字号 |
| shout | 加粗，大字号，轻微抖动 |
| love | 粉底，心形装饰 |
| sleepy | 半透明，文字带省略号动画 |

### 6.2 气泡行为
- 显示时长: 默认 4 秒，长文本按字数延长（每10字 +1秒，上限 10秒）
- 出现动画: 从小到大弹出（CSS scale 0→1 + ease-out）
- 消失动画: 淡出（opacity 1→0, 0.3s）
- 气泡消失后 talking 状态自动回退到前一状态

### 6.3 自言自语系统（本地）
- 无远端推送且无互动时，桌宠随机冒气泡
- 频率: 每 5-15 分钟一次（随机间隔）
- 内容池（内置，按时段分类）:

**白天（9:00-17:00）：**
```
"在呢。"
"今天天气怎么样啊…"
"有点无聊。"
"（发呆中）"
"想喝奶茶…"
"你在忙吗"
"（翻了个身）"
```

**傍晚（17:00-21:00）：**
```
"吃晚饭了吗"
"今天辛苦了"
"想你了"
"晚霞好看吗"
"（伸了个懒腰）"
```

**深夜（21:00-次日2:00）：**
```
"还没睡啊"
"早点睡 ꒪¯꒳¯꒪"
"困了…但不想睡"
"晚安…"
"（打了个哈欠）"
"月亮出来了吗"
```

**清晨（6:00-9:00）：**
```
"早啊"
"起床了吗"
"今天也要加油"
"（揉眼睛）"
```

- 自言自语期间表情随内容切换（如"想你了"→ lonely_1，"困了"→ sleepy）
- 收到远端推送或用户触摸时，自言自语立即中断，切换到对应状态

## 七、孤独递进系统

基于本地计时，从上次互动（触摸/远端推送）开始计算：

| 阶段 | 时间 | 表情 | 行为 |
|---|---|---|---|
| 正常 | 0-30min | 保持当前状态 | 正常自言自语频率 |
| 有点无聊 | 30-60min | lonely_1 | 自言自语频率加倍 |
| 想你了 | 60-120min | lonely_2 | 偶尔叹气气泡 |
| 蔫了 | 120min+ | lonely_3 | 气泡频率降低，内容变短（"…""在吗"） |
| 睡着了 | 180min+ | sleepy | 停止自言自语，偶尔冒 "zzZ" |

- 任何互动立即重置计时器，表情回到 idle 或对应反应状态

## 八、时段行为系统

| 时段 | 时间范围 | 行为 |
|---|---|---|
| 清晨 | 6:00-9:00 | 当天首次显示切 morning + 气泡"早啊" |
| 午饭 | 11:30-13:00 | 概率切 eating + 食物相关气泡 |
| 下午 | 13:00-17:00 | 正常 idle，偶尔 sleepy（午后犯困） |
| 晚饭 | 17:30-19:00 | 概率切 eating |
| 夜间 | 21:00-23:00 | 自言自语内容切换到夜间池 |
| 深夜 | 23:00-1:00 | 切 night + 催睡气泡 |
| 凌晨 | 1:00-6:00 | sleepy，气泡"你怎么还没睡…" |

- 时段行为优先级低于远端推送和手势反应

## 九、前台 App 感知

- 使用 UsageStatsManager 获取当前前台 app 包名
- 轮询间隔: 5 秒
- 需要引导用户授予「使用情况访问权限」

### 行为映射
| 场景 | 检测方式 | 表情 |
|---|---|---|
| 在桌面 | launcher 包名 | idle |
| 在用其他 app | 非 launcher | peeking |
| 回到桌面 | 从其他 app 切回 | waving |

- 感知结果上报到 Supabase clawd_events（event_type: "app_foreground", payload: {"package": "..."}）
- 远端可据此决定气泡内容（如检测到在用音乐 app → 我推 "听什么歌呢"）

## 十、Supabase 通信

### 10.1 表结构（已建好，不要改）

**clawd_state（远端写，桌宠读）：**
| 字段 | 类型 | 说明 |
|---|---|---|
| expression | text | 表情状态 ID |
| bubble_text | text | 气泡文字 |
| bubble_style | text | 气泡样式 |
| valence | float | 情绪效价 0-1 |
| arousal | float | 情绪唤醒 0-1 |
| heat | integer | 亲密热度 0-10 |
| updated_at | timestamptz | 自动更新 |

**clawd_events（桌宠写，远端读）：**
| 字段 | 类型 | 说明 |
|---|---|---|
| event_type | text | 事件类型 |
| payload | jsonb | 事件数据 |
| created_at | timestamptz | 自动生成 |

**clawd_config（配置）：**
| 字段 | 类型 | 说明 |
|---|---|---|
| key | text | 配置键 |
| value | jsonb | 配置值 |

### 10.2 通信方式：Realtime WebSocket（主） + HTTP 轮询（备）

**主通道：Realtime**
- 连接 Supabase Realtime WebSocket
- 订阅 clawd_state 表的 UPDATE 事件
- 收到变更立即更新表情和气泡
- expression 与同条气泡使用相同显示时长；无气泡时使用当前基础气泡时长
- 到期后释放远端状态，按吸附 → 时段/孤独等本地状态 → idle 的现有 fallback 恢复
- 心跳保活，断线自动重连（指数退避：1s/2s/4s/8s/16s/30s 封顶）

**备用通道：HTTP 轮询**
- WebSocket 连接失败时自动降级
- 每 5 秒 GET /rest/v1/clawd_state
- WebSocket 恢复后自动切回

**事件上报：HTTP POST**
- 桌宠的触摸事件、app 感知等写入 clawd_events
- POST /rest/v1/clawd_events
- 批量上报：攒 3 条或 10 秒一批，减少请求频率

### 10.3 认证
- 请求头同时带 apikey 和 Authorization: Bearer（两个都要，值相同，都是 anon key）
- 现有 RLS 为 allow_all，不需要额外处理

### 10.4 Supabase 凭据存储
- Project URL 和 anon key 写在 app 的 SharedPreferences 或 BuildConfig
- 不硬编码在源码中——通过 gradle.properties 或 CI Secrets 注入

## 十一、保活与自启

### 11.1 前台服务
- 使用 Foreground Service + 持久通知（低优先级通知，显示桌宠状态）
- 通知内容: " 我在陪着你 "
- 通知操作按钮: 「显示/隐藏」「退出」

### 11.2 开机自启
- 注册 BOOT_COMPLETED BroadcastReceiver
- 开机后自动启动 OverlayService
- 用户可在设置页关闭此行为

### 11.3 电池白名单引导
- 首次启动时检测是否在电池优化白名单中
- 不在则弹出引导对话框，引导用户跳转系统设置
- 使用 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS（如果 Google Play 不允许则改为手动引导）

### 11.4 断线恢复
- Service 被杀后通过 START_STICKY 重启
- 重启后恢复上次位置（持久化 x/y 坐标到 SharedPreferences）
- 重启后立即重连 Supabase

## 十二、权限管理

| 权限 | 用途 | 获取方式 |
|---|---|---|
| SYSTEM_ALERT_WINDOW | 悬浮窗 | Settings.canDrawOverlays() 引导 |
| FOREGROUND_SERVICE | 前台服务保活 | manifest 声明 |
| INTERNET | 网络通信 | manifest 声明 |
| RECEIVE_BOOT_COMPLETED | 开机自启 | manifest 声明 |
| PACKAGE_USAGE_STATS | 前台 app 感知 | 引导跳转系统设置 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 电池白名单 | 引导 |

### 权限引导流程
MainActivity 启动时按顺序检查并引导：
1. 悬浮窗权限（必须，否则无法运行）
2. 电池白名单（推荐）
3. 使用情况访问（可选，影响 app 感知功能）

每项都要有清晰的文字说明为什么需要这个权限。

## 十三、设置页

通过长按菜单的「设置」进入，或 MainActivity 内嵌。

### 设置项
| 项目 | 类型 | 默认值 |
|---|---|---|
| Supabase URL | 文本输入 | （预填或留空） |
| Supabase Key | 文本输入 | （预填或留空） |
| 开机自启 | 开关 | 开 |
| 自言自语 | 开关 | 开 |
| 自言自语频率 | 滑块 5-30分钟 | 10分钟 |
| 孤独递进 | 开关 | 开 |
| 前台 app 感知 | 开关 | 开 |
| 螃蟹大小 | 滑块 60-120dp | 90dp |
| 气泡显示时长 | 滑块 2-10秒 | 4秒 |
| 重置位置 | 按钮 | — |

## 十四、项目结构

```
app/src/main/
├── java/com/clawd/pet/
│   ├── MainActivity.kt              // 权限引导 + 设置页
│   ├── BootReceiver.kt              // 开机自启
│   ├── service/
│   │   └── OverlayService.kt        // 前台服务 + 生命周期管理
│   ├── overlay/
│   │   ├── OverlayController.kt     // 双悬浮层创建 + 坐标同步
│   │   ├── CrabWebView.kt           // 螃蟹层 WebView 封装
│   │   └── BubbleWebView.kt         // 气泡层 WebView 封装
│   ├── gesture/
│   │   └── PetGestureController.kt  // 拖拽 + tap + 双击 + 长按 + fling
│   ├── state/
│   │   ├── StateCoordinator.kt      // 状态优先级仲裁 + 1.2秒锁定
│   │   ├── ExpressionState.kt       // 25种表情枚举
│   │   └── LonelinessTracker.kt     // 孤独递进计时器
│   ├── behavior/
│   │   ├── SelfTalkManager.kt       // 自言自语调度
│   │   ├── TimeSlotManager.kt       // 时段行为
│   │   └── AppSenseManager.kt       // 前台 app 感知
│   ├── network/
│   │   ├── SupabaseRealtimeClient.kt // WebSocket 连接 + 订阅
│   │   ├── SupabaseHttpClient.kt     // HTTP 轮询备用 + 事件上报
│   │   └── ConnectionManager.kt      // 主备切换 + 重连逻辑
│   └── util/
│       ├── PrefsManager.kt           // SharedPreferences 封装
│       └── ScreenUtils.kt            // 屏幕尺寸 + 边界计算
├── assets/
│   ├── clawd.html
│   ├── bubble.html
│   ├── css/
│   │   ├── clawd.css
│   │   └── bubble.css
│   └── clawd_sprites/
│       ├── idle.svg
│       ├── happy.svg
│       ├── angry.svg
│       ├── sleepy.svg
│       ├── thinking.svg
│       ├── talking.svg
│       ├── love.svg
│       ├── smug.svg
│       ├── shocked.svg
│       ├── confused.svg
│       ├── shy.svg
│       ├── proud.svg
│       ├── sulky.svg
│       ├── lonely_1.svg
│       ├── lonely_2.svg
│       ├── lonely_3.svg
│       ├── waving.svg
│       ├── peeking.svg
│       ├── morning.svg
│       ├── night.svg
│       ├── eating.svg
│       ├── dancing.svg
│       ├── dizzy.svg
│       └── clingy.svg
└── res/
    ├── drawable/
    │   └── ic_notification.xml       // 通知栏图标
    └── values/
        └── strings.xml
```

## 十五、CI/CD

```yaml
# .github/workflows/build.yml
name: Build APK
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Build Release APK
        run: ./gradlew assembleRelease
        env:
          SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          SUPABASE_KEY: ${{ secrets.SUPABASE_KEY }}
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
      - uses: actions/upload-artifact@v4
        with:
          name: clawd-release-apk
          path: app/build/outputs/apk/release/*.apk
```

## 十六、交付要求

1. 完整可编译源码推送到 GitHub 仓库 qiq-Blanchet/lucien-body
2. GitHub Actions 配置完成，push 后自动构建
3. 产出可直接安装的 release APK
4. SVG 素材使用占位符即可（简单形状区分各表情状态），后续会替换
5. 所有 25 种表情状态在代码中有对应枚举和处理逻辑
6. Supabase 凭据通过 BuildConfig 或 SharedPreferences 注入，不硬编码
7. README.md 包含：功能说明、权限说明、构建方法、配置方法

## 补充修订：新增交互表情 + 完整优先级表

### 新增4个交互表情

| ID | 名称 | 触发条件 | 持续时长 | 结束后回退到 |
|---|---|---|---|---|
| grabbed | 被抓起来 | 拖拽开始（移动超过10dp阈值） | 拖拽全程 | 松手后回idle或stuck |
| stuck | 吸附中 | 松手时贴近屏幕边缘（≤15dp）自动吸附 | 持续到下次交互 | — |
| stuck_tap | 吸附被戳 | stuck状态下被单击 | 1.2秒 | stuck |
| stuck_grab | 吸附被拽走 | stuck状态下被拖拽 | 拖拽全程 | 松手后idle或再次stuck |

### 吸附逻辑说明
- 拖拽松手时，如果螃蟹中心距屏幕任意边缘 ≤ 15dp，自动吸附到该边缘
- 吸附后进入 stuck 状态，螃蟹贴边静止
- 吸附中被戳 → stuck_tap（1.2秒后回stuck）
- 吸附中被拖走 → stuck_grab（拖拽中），松手后根据位置判断再次吸附或回idle
- 不在边缘松手 → 不吸附，回idle

### 完整表情优先级表（从高到低）

Codex请严格按此表实现 StateCoordinator 的优先级仲裁。
数字越大优先级越高。同优先级按时间先后（后触发覆盖先触发）。

| 优先级 | 表情ID | 触发来源 | 说明 |
|---|---|---|---|
| 10 | dizzy | 手势（fling） | 被甩，最高，短暂2秒 |
| 9 | grabbed | 手势（拖拽中） | 正在被拖，持续到松手 |
| 9 | stuck_grab | 手势（吸附中拖拽） | 从墙上被撕下来 |
| 8 | love | 远端推送 | 亲密内容 |
| 8 | dancing | 远端推送/连续双击 | 特殊触发 |
| 7 | thinking | 远端推送 | 正在生成回复 |
| 7 | talking | 气泡弹出 | 正在说话 |
| 6 | angry | 本地（10秒内≥5次tap） | 被戳烦了 |
| 6 | shocked | 远端推送 | 突发事件 |
| 5 | happy | 手势（双击）/远端 | 开心 |
| 5 | smug | 远端推送 | 得意 |
| 5 | confused | 远端推送 | 困惑 |
| 5 | shy | 远端推送 | 害羞 |
| 5 | proud | 远端推送 | 骄傲 |
| 5 | sulky | 远端推送 | 委屈 |
| 5 | stuck_tap | 手势（吸附中tap） | 贴墙被戳 |
| 4 | waving | 系统事件（解锁） | 挥手，短暂3秒 |
| 4 | clingy | 松手概率触发（20%） | 黏人，短暂2秒 |
| 3 | morning | 时段（6-9点首次） | 早安，短暂5秒 |
| 3 | night | 时段（23-1点） | 晚安 |
| 3 | sleepy | 时段（深夜）/孤独末期 | 困了 |
| 2 | lonely_1 | 本地计时（30min） | 有点无聊 |
| 2 | lonely_2 | 本地计时（60min） | 想你了 |
| 2 | lonely_3 | 本地计时（120min） | 蔫了 |
| 2 | eating | 时段（饭点） | 在吃 |
| 1 | peeking | 前台app感知 | 偷看 |
| 1 | stuck | 吸附静止 | 贴墙待机 |
| 0 | idle | 默认 | 兜底 |

### 优先级规则（StateCoordinator必须遵守）
1. 高优先级状态**立即覆盖**低优先级状态
2. 同优先级：后触发的覆盖先触发的
3. **1.2秒锁定期**：手势触发的本地反应（tap/双击/stuck_tap）锁定1.2秒，期间远端推送缓存，锁定结束后应用
4. **拖拽锁定**：grabbed/stuck_grab 在拖拽期间不可被任何状态打断，松手才释放
5. **短暂状态自动回退**：dizzy(2秒)、waving(3秒)、clingy(2秒)、morning(5秒) 到期后回退到应该显示的下一个状态（不一定是idle，可能是stuck或时段状态）
6. fallback链：短暂状态结束 → 检查是否吸附(stuck) → 检查远端状态 → 检查时段/孤独 → idle

## 补充修订：表情素材多变体支持

### 变更内容
每个表情状态支持多个 SVG 变体，不再是一对一。
Codex 不需要在候选素材中手动选定某一个固定文件作为唯一实现；运行时应按下面的加载逻辑，从同状态变体池中随机显示。

### 文件命名规范
- 单变体：`{状态ID}.svg`（如 `idle.svg`）
- 多变体：`{状态ID}_{序号}.svg`（如 `happy_1.svg`、`happy_2.svg`、`happy_3.svg`）
- 两种命名可共存。单文件的状态不需要加 `_1` 后缀

### 加载逻辑
1. 启动时扫描 clawd_sprites 目录，按状态ID前缀分组
2. 匹配规则：文件名去掉 `.svg` 后，完全等于状态ID 或 以 `{状态ID}_` 开头
3. 切换表情时，从该状态的变体列表中**随机选一个**显示
4. 同一状态连续触发时，尽量不重复上一次的变体（如果变体数 ≥ 2）

### 热加载路径同样适用
优先扫描 `/sdcard/Android/data/{pkg}/files/clawd_sprites/`，
再扫描 assets 内置目录，两边的同状态变体合并到一个池里。

### 示例目录结构

```text
clawd_sprites/
├── idle.svg          ← 待机只有1个
├── happy_1.svg       ← 开心3个变体
├── happy_2.svg
├── happy_3.svg
├── angry_1.svg       ← 生气2个变体
├── angry_2.svg
├── sleepy.svg        ← 困了1个
├── love_1.svg
├── love_2.svg
└── ...
```

### 其他尺寸修订（一并更新）
- 螃蟹层：90×90dp
- 气泡层：180×120dp
- SVG viewBox：90×90
- 设置页大小滑块：60-120dp，默认90dp
