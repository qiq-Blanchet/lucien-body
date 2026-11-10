# Luc 桌宠项目维护参考

本文记录 Luc Android 桌宠截至 **2026-11-10 / v0.1.26（versionCode 27）** 的实际实现。它用于后续修改、回归检查和交接；若文档与代码冲突，以当前代码和测试为准，并在同一次修改中更新本文。

## 1. 项目边界

| 项目 | 当前值 |
| --- | --- |
| 应用名称 | Luc |
| Android applicationId | `com.luc.body` |
| 支持版本 | Android 8.0+（`minSdk 26`） |
| 编译/目标版本 | `compileSdk 36` / `targetSdk 34` |
| Java/Kotlin 目标 | JVM 17 |
| 当前版本 | `0.1.26` / `versionCode 27` |
| 桌宠本体 | Android 前台服务 + 系统悬浮窗 + WebView SVG 渲染 |
| 远端状态 | Supabase Postgres、Realtime、REST |
| 远程工具 | Supabase Edge Function 提供 Streamable HTTP MCP |
| 设备端 AI | 无；APK 不运行 LLM，只消费远端状态并上报互动事件 |

主要入口：

- `MainActivity`：设置、权限状态、启动/停止、重置位置。
- `OverlayService`：前台服务、行为模块和网络模块的生命周期总控。
- `OverlayController`：宠物、气泡、情绪面板、长按菜单四类悬浮窗。
- `StateCoordinator`：远端状态、本地互动和环境状态的仲裁与定时。
- `WebRenderer`：加载宠物 SVG 和气泡 HTML/CSS。
- Supabase：`clawd_state` 保存当前状态，`clawd_events` 保存互动，`clawd_config` 保留配置。

## 2. 原有功能与后续补充

### 2.1 MVP/V2 原有能力

项目早期 MVP 和 V2 已建立以下基础：

- 前台服务常驻，宠物层可触摸，气泡层触摸穿透。
- Supabase 状态读取、Realtime 更新、REST 轮询降级和互动事件批量上报。
- 28 个具名表情状态、37 份内置 SVG 变体和外部素材覆盖。
- 单击、双击、长按、拖动、甩动、侧边吸附等互动。
- 时段行为、孤独递进、自言自语、前台 App 感知。
- 开机自启、位置保存、通知栏显示/隐藏和退出。
- GitHub Actions 测试、lint、签名构建和 APK 契约检查。

### 2.2 当前版本补充或重做的部分

- 增加 `valence`、`arousal`、`heat` 三项情绪值及 88×88dp 情绪面板。
- 增加并美化 `normal/whisper/shout/love/sleepy` 五种气泡；`whisper` 为淡紫轻声样式，`sleepy` 不带月牙。
- 单击使用 `shout`，双击和摸头使用 `love`；本地文字不改变原文。
- 长按同时显示情绪面板和三按钮菜单；菜单只有“戳一下 / 摸摸头 / 隐藏”，不再包含“设置”。
- 长按菜单按宠物位置自适应：通常在脚下，靠近底部时放到头顶，侧边吸附时贴对应屏幕边。
- 左右吸附阈值收紧到 4dp；不做顶部或底部吸附。
- 手动点击“启动 Luc”时将宠物重置到屏幕中上部，避免宠物丢出屏幕后无法找回。
- 主界面和设置弹层重新美化；启动图使用现有 Clawd 静态素材；不再自动弹出权限说明框。
- 宠物尺寸固定 90dp、气泡/情绪面板显示周期固定 10 秒；已移除无效的尺寸和气泡时长滑杆。
- 自言自语频率改为 30–60 分钟，并与设置值实际联动。
- 新增 `luc_update/luc_get_status/luc_get_events` MCP；一次调用可同时写入表情、文字、气泡样式和三项情绪值。
- MCP 用词统一为 `Luc clawd`，互动事件说明消除“你/用户”的歧义。

## 3. 当前可见行为

### 3.1 四类悬浮窗

| 悬浮窗 | 尺寸 | 可触摸 | 说明 |
| --- | ---: | --- | --- |
| 宠物 | 90×90dp | 是 | 拖动、点击、长按和甩动入口 |
| 气泡 | 180×120dp | 否 | HTML/CSS 渲染，触摸穿透 |
| 情绪面板 | 88×88dp | 否 | 显示心情、活力、亲密三项数值 |
| 长按菜单 | 88×112dp | 是 | 三个纵向按钮 |

初始位置为安全显示区域的水平居中、垂直约四分之一处。窗口使用安全边界计算，不直接按某一手机的像素写死；视觉验收主要在一加 Ace 2 Pro（1240×2772）完成。

### 3.2 位置与吸附

- 宠物只在左、右屏幕边吸附，触发距离为 4dp。
- SVG 画布自带透明边距。吸附后窗口会横向越界约宠物宽度的三分之一，使可见蟹身贴到屏幕边；不要把这个越界误判为坐标错误。
- 顶部允许窗口向安全区外越过约宠物高度的三分之二，同样用于抵消 SVG 顶部透明区。
- 不吸附顶部或底部。
- 拖动结束后保存位置；开机自启优先恢复保存位置。
- 主界面的“启动 Luc”和“重置位置”会清除保存位置并回到固定中上位置。

### 3.3 气泡位置和样式

- 默认在宠物上方；上方空间不足时翻到宠物下方。
- `normal`：暖白普通气泡。
- `whisper`：更明显的淡紫背景、紫色文字和虚线描边。
- `shout`：强调式大声气泡。
- `love`：粉色爱心气泡。
- `sleepy`：困倦风格，无月牙装饰。
- Android 服务把基础时长固定为 10 秒；每次新气泡会替换上一条并重新开始完整的 10 秒，不应中途消失后再次浮现。
- MCP 接受最长 500 个字符，但当前客户端 `bubble.html` 最多渲染前 120 个字符。修改任一上限时必须同步检查服务端验证、数据库内容和客户端截断。

### 3.4 情绪面板

- 固定 88×88dp，内部文字、进度条和留白按同一比例缩放。
- 与可见蟹身保持 20dp 水平间距；优先放左侧，空间不足再放右侧，垂直方向保持与宠物居中。
- 宠物进入屏幕顶部透明越界区、导致面板被安全边界向下夹紧时，可见文字气泡会同步下移相同距离；面板恢复默认位置后气泡也恢复原位。
- 长按时读取并显示最近一次已知情绪值，同时显示三按钮菜单。
- 首次收到远端情绪值只初始化缓存，不单独弹面板。
- 后续远端更新包含文字时，气泡和最新情绪面板同时出现并同时保持 10 秒。
- 后续远端更新不含文字时，只有情绪值确实变化才单独显示面板。

### 3.5 长按菜单

- 长按阈值 500ms。
- 三个按钮依次为“戳一下 / 摸摸头 / 隐藏”。
- 通常放在宠物脚下，菜单顶部与宠物窗口重叠 4dp，形成宠物踩住菜单的视觉效果。
- 底部空间不足时放到宠物上方；37dp 的可见偏移用于避开 SVG 透明画布，使菜单靠近宠物头部。
- 左右吸附时菜单贴同一侧安全边界。
- 菜单出现时情绪面板仍应同时可见，二者不得互相遮挡。

## 4. 互动、环境与事件

### 4.1 手势

| 操作 | 本地表现 | 上报事件 |
| --- | --- | --- |
| 单击 | `happy` + “在呢。” + `shout` | `tap` |
| 吸附时单击 | `stuck_tap` + 原单击文字 + `shout` | `stuck_tap` |
| 双击 | `happy` + 随机文字 + `love` + 爱心 | `double_tap` |
| 摸摸头 | `happy` + “被摸了 ꒪¯꒳¯꒪” + `love` | `pet_head` |
| 开始拖动 | `grabbed`；从侧边抓起为 `stuck_grab` | `drag_start` |
| 结束拖动 | 回到吸附或自由状态；自由释放有 20% 概率短暂 `clingy` | `drag_end` |
| 四向甩动 | `dizzy` | `fling` |

触摸位移阈值 10dp，双击窗口 300ms。10 秒内累计 5 次物理点击触发短暂 `angry`。

### 4.2 状态优先级

远端状态具有绝对优先级；存在远端状态时，本地点击、自言自语和环境状态不会覆盖它。其后依次是拖动状态，以及按“优先级 + 最新序列”选择的本地/环境候选。

| 优先级 | 表情 |
| ---: | --- |
| 10 | `dizzy` |
| 9 | `grabbed`, `stuck_grab` |
| 8 | `love`, `dancing` |
| 7 | `thinking`, `talking` |
| 6 | `angry`, `shocked` |
| 5 | `happy`, `smug`, `confused`, `shy`, `proud`, `sulky`, `stuck_tap` |
| 4 | `waving`, `clingy` |
| 3 | `morning`, `night`, `sleepy` |
| 2 | `lonely_1`, `lonely_2`, `lonely_3`, `eating` |
| 1 | `peeking`, `stuck` |
| 0 | `idle` |

本地瞬态默认时长：`dizzy` 2 秒、`waving` 3 秒、`clingy` 2 秒、`morning` 5 秒。远端状态由统一的 10 秒生命周期管理，表情、气泡和情绪面板一起到期。

### 4.3 自言自语与孤独递进

- 自言自语默认开启，频率可设 30–60 分钟，气泡固定 10 秒。
- 远端状态或用户互动会重置相关计时，防止远端表达被本地话语打断。
- 未互动少于 30 分钟：正常。
- 30–60 分钟：`lonely_1`，自言自语间隔缩短。
- 60–120 分钟：`lonely_2`，使用叹气类话语。
- 120–180 分钟：`lonely_3`，使用更短的话语。
- 180 分钟以上：`sleepy`。

### 4.4 时段行为

| 时间 | 行为 |
| --- | --- |
| 01:00–06:00 | `sleepy`，每日一次“你怎么还没睡…” |
| 06:00–09:00 | 每日一次 `morning` + “早啊” |
| 11:30–13:00 | 50% `eating` + 午间文字 |
| 13:00–17:00 | 50% `sleepy` |
| 17:30–19:00 | 50% `eating` + 晚间文字 |
| 23:00–01:00 | `night`，每日一次睡前文字 |

服务每 60 秒刷新一次时段状态。

### 4.5 前台 App 感知

- 需要“使用情况访问”权限；关闭开关或未授权时不运行。
- 每 5 秒检查一次前台包名，只在包名变化时上报 `app_foreground`，payload 中使用 `package` 字段。
- 桌面启动器对应 `idle`；从其他 App 返回桌面会短暂 `waving`。
- 其他 App 对应 `peeking`，持续 10 秒。
- 此功能只读取前台包名，不读取 App 内文本或内容。

## 5. SVG 与渲染

- `app/src/main/assets/clawd_sprites/` 包含 37 份内置 SVG，对应 28 个状态。
- 同一表情有多个变体时随机选择；当前变体至少完整播放其最长动画周期后才允许更换，并避免连续立即重复。
- 原始 SVG 不在运行时改写；加载阶段只把小写 `viewbox` 规范化为 `viewBox`。
- 外部覆盖目录是 App 专属外部文件目录下的 `clawd_sprites/`。同名外部文件优先于内置文件。
- 外部加载器只接受普通 `.svg` 文件，并防止路径穿越和符号链接逃逸。替换素材时仍要保持原有画布比例和透明边距，否则吸附、气泡和菜单的“视觉距离”会变化。

## 6. 主界面、权限与生命周期

### 6.1 当前设置

- 开机自启。
- 自言自语开关。
- 自言自语频率 30–60 分钟。
- 孤独递进开关。
- 前台 App 感知开关。
- Supabase URL 和 publishable key。

`PrefsManager` 仍保留旧版 `petSizeDp` 和 `bubbleDurationSeconds` 字段以兼容已有偏好，但当前 UI 不展示，服务也不使用：宠物固定 90dp、气泡固定 10 秒。不要只恢复滑杆而不恢复完整的数据流和回归测试。

### 6.2 权限

| 权限/系统设置 | 用途 | 是否阻止启动 |
| --- | --- | --- |
| 显示在其他应用上层 | 创建四类悬浮窗 | 是 |
| 通知 | 前台服务状态、显示/隐藏、退出 | Android 13+ 建议，不作为核心启动门槛 |
| 忽略电池优化 | 降低后台服务/网络被中断概率 | 否 |
| 使用情况访问 | 前台 App 感知 | 否 |
| 开机完成广播 | 按设置自动启动 | 由开机自启开关决定 |

应用打开时只刷新权限状态，不自动弹权限说明。Android 16 从侧载 APK 安装时，悬浮窗和使用情况访问可能需要先在应用详情右上角允许“受限设置”。

### 6.3 启停行为

- “启动 Luc”：要求悬浮窗权限，启动前台服务并重置位置。
- “停止 Luc”或通知“退出”：停止服务并移除全部悬浮窗。
- 通知“隐藏”：只移除悬浮窗，服务和状态管理继续运行；“显示”重新创建悬浮窗。
- 开机自启：仅在开关开启且已有悬浮窗权限时启动，并恢复保存位置。

## 7. Supabase 数据流

### 7.1 客户端连接

- URL 必须是 `https://*.supabase.co`。
- 当前 Android 配置只接受 `sb_publishable_...` publishable key。
- REST 和 WebSocket 使用 publishable key；publishable key 会进入 APK，因此不是秘密。安全边界必须由 RLS、GRANT 和列级权限提供。
- `clawd_state` 通过 Realtime 接收 UPDATE；WebSocket 加入超时 10 秒，心跳 25 秒。
- Realtime 不可用时由 REST 每 5 秒轮询最新状态。
- 互动事件满 3 条立即上报，否则 10 秒批量刷新；失败批次会重新入队。
- 远端 `updated_at` 必须是可解析的 ISO 8601 时间，旧版本或重复版本会被忽略。

### 7.2 当前线上表

截至本文快照，线上三张表均启用 RLS：

| 表 | 关键列 | 用途 |
| --- | --- | --- |
| `clawd_state` | `id`, `expression`, `bubble_text`, `bubble_style`, `valence`, `arousal`, `heat`, `updated_at` | 当前远端状态 |
| `clawd_events` | `id`, `event_type`, `payload`, `created_at` | 桌宠互动和前台 App 变化 |
| `clawd_config` | `key`, `value`, `updated_at` | 保留的远端配置 |

仓库迁移为匿名低权限角色开放 `clawd_state`、`clawd_events` 的读取，并只允许 MCP 更新 `clawd_state` 的六列：`expression/bubble_text/bubble_style/valence/arousal/heat`。不要扩大为整表 UPDATE，也不要给公开客户端 service role/secret key。

## 8. MCP

详细连接示例见 [luc-mcp.md](luc-mcp.md)。当前线上 Edge Function：

- slug：`luc-mcp`
- 状态：ACTIVE
- 部署版本：5
- MCP server version：`1.1.0`
- 支持协议：`2025-03-26`、`2025-06-18`、`2025-11-25`
- `verify_jwt=false`；函数内部自行读取 publishable/legacy anon key、拒绝 `service_role` 和 `sb_secret_...`，再用受 RLS/GRANT 约束的 REST 请求验证权限。

### 8.1 `luc_update`

一次调用至少传一项，可在同一笔更新中写入：

- `expression`：28 个支持状态之一。
- `bubble_text`：服务端最多 500 字符；空字符串清除气泡。
- `bubble_style`：`normal/whisper/shout/love/sleepy`，必须与 `bubble_text` 同传。
- `valence`：0–1。
- `arousal`：0–1。
- `heat`：0–10 的整数。

未传的表情和情绪值保持原值；**未传 `bubble_text` 会主动清掉旧气泡并把样式恢复为 `normal`**，防止旧文字在只改情绪时重新播放。

### 8.2 `luc_get_status`

无参数，只读当前表情、气泡、三项情绪值和更新时间。

### 8.3 `luc_get_events`

- `limit`：1–100，默认 20。
- `event_type`：可选，限定 `tap/double_tap/pet_head/drag_start/drag_end/fling/stuck_tap/app_foreground`。
- `since`：可选 ISO 8601 时间。
- 结果从新到旧排列，不删除、不标记已读。

### 8.4 密钥注意事项

- Android 和第三方 MCP 客户端只使用 publishable key（或迁移期 legacy anon key）。
- `service_role`、`sb_secret_...`、数据库密码、签名密码绝不能放进 APK、仓库、MCP 客户端配置或截图。
- Supabase 当前官方指南建议新 publishable key 作为 `apikey` 使用，并在 `verify_jwt=false` 时由函数自行鉴权；本项目函数兼容 `Authorization: Bearer ...` 和 `apikey` 两种入口。平台规则变化时先做线上兼容测试，再改客户端头部。

## 9. 代码地图

| 路径 | 责任 |
| --- | --- |
| `app/src/main/java/com/luc/body/MainActivity.kt` | 设置页、权限、启停、重置位置 |
| `app/src/main/java/com/luc/body/OverlayService.kt` | 前台服务总控与模块装配 |
| `app/src/main/java/com/luc/body/overlay/` | 四类悬浮窗、几何、位置跟踪 |
| `app/src/main/java/com/luc/body/state/` | 状态模型、优先级、定时、情绪缓存 |
| `app/src/main/java/com/luc/body/gesture/` | 点击、双击、长按、拖动、甩动分类 |
| `app/src/main/java/com/luc/body/behavior/` | 时段、孤独、自言自语、App 感知 |
| `app/src/main/java/com/luc/body/network/` | REST、Realtime、轮询、事件批处理 |
| `app/src/main/java/com/luc/body/sprite/` | SVG 清单、随机池、外部覆盖和安全读取 |
| `app/src/main/java/com/luc/body/web/` | WebView 命令、渲染门控 |
| `app/src/main/assets/clawd_sprites/` | 37 份内置 SVG |
| `app/src/main/assets/css/bubble.css` | 五种气泡视觉 |
| `supabase/functions/luc-mcp/index.ts` | Streamable HTTP MCP 实现 |
| `supabase/migrations/` | 表、RLS、Realtime、列级授权 |
| `docs/luc-mcp.md` | MCP 连接和调用说明 |
| `.github/workflows/build.yml` | CI、签名构建和 artifact 上传 |
| `scripts/verify-apk-contract.sh` | APK 黑盒契约检查 |

## 10. 构建、签名与发布

本地验证：

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleDebug
```

GitHub Actions 在以下场景运行：

- push 到 `main`：单测、release lint、签名 release APK、APK 契约、签名验证、artifact 上传。
- 针对 `main` 的 pull request：单测、debug lint、debug APK。
- `workflow_dispatch`：与 main 发布流程相同。

发布流程依赖六个 Actions secrets：

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

不要在日志、文档或命令历史中输出其值。覆盖安装必须继续使用同一份 keystore、alias 和密码；更换或丢失签名将导致已安装正式版无法升级。当前工作流 artifact 名为 `Luc-0.1.26-release.apk`，保留 30 天。

## 11. 修改时的关键注意事项

1. **先确认修改的是窗口坐标还是可见素材。** SVG 有大面积透明区，窗口对齐正确不等于蟹身视觉对齐正确。
2. **不要同时改变无关位置。** 气泡上方和下方是两条独立分支；只修上方时必须给下方添加/保留回归断言。
3. **保持四个窗口独立。** 宠物移动可以带动其他窗口重算，但气泡、面板和菜单不能改变宠物坐标。
4. **远端状态优先。** 若要改变优先级，必须覆盖远端、拖动、本地点击、时段、孤独、自言自语的组合测试。
5. **同一可见状态只保留一个生命周期。** 新气泡应取消旧定时器并连续显示完整 10 秒；不要让渲染回调再次启动第二个计时器。
6. **情绪面板不是每次网络读取都弹。** 首次只缓存；有新文字时同步显示；无文字时仅数值变化才显示。
7. **不要恢复无效设置。** 旧宠物尺寸和气泡时长偏好仍在，但当前产品明确固定为 90dp/10 秒。
8. **MCP 更新的清气泡语义是有意设计。** 只改表情或情绪时，旧 `bubble_text` 会被清除，避免 App 重放上一句话。
9. **publishable key 不是秘密但权限必须最小。** 任何新增表/列都先设计 RLS、GRANT 和 MCP 输入校验；绝不把 secret/service role 下发到客户端。
10. **数据库迁移不可只改线上。** 所有 schema/RLS/GRANT 变更必须写入 `supabase/migrations/`，Edge Function 源码必须与线上版本同步。
11. **签名不可替换。** 发布前检查 APK 证书与上一正式版一致。
12. **设备实测仍不可省略。** JVM 单测能验证几何和状态机，但 OEM 悬浮窗、字体缩放、状态栏/手势区、Doze 和受限设置仍需真机检查。

## 12. 每次改动的最小回归清单

### 视觉/悬浮层

- 中间、顶部、底部、左吸附、右吸附五种位置。
- 气泡上方和翻转到下方的距离均未被误改。
- 长按时菜单和情绪面板同时出现且互不遮挡。
- 88dp 情绪面板内部无裁切，三项数值和进度条对齐。
- 五种气泡样式能从真实远端输入生效。

### 交互/计时

- 单击、双击、摸头、拖动、吸附、甩动、五连击。
- 新气泡连续显示 10 秒，只消失一次。
- 本地互动不会覆盖正在显示的远端状态。
- App 重开、服务隐藏/显示、停止/启动、开机恢复的位置符合预期。

### Supabase/MCP

- Realtime 更新能到达；断开后 REST 轮询仍能更新。
- 事件能批量写入 `clawd_events`。
- `tools/list` 返回三个工具。
- `luc_update` 能一次更新六类输入并返回新状态。
- `luc_get_status`、`luc_get_events` 可读，事件顺序从新到旧。
- publishable key 可用；secret/service role 被拒绝；日志不泄露任何 key。

### 发布

- `testDebugUnitTest`、`lintRelease`、`assembleDebug` 通过。
- CI release 构建通过 APK 契约与签名验证。
- 正式 APK 包名、版本号、签名证书、Supabase BuildConfig 均正确。

## 13. 已知限制

- Android Doze 和厂商后台策略仍可能终止服务或网络；电池白名单只能降低概率。
- 前台 App 感知依赖系统 Usage Access，在不同 ROM 上可能延迟。
- 真机悬浮窗视觉受系统字体缩放、显示缩放、安全区和 OEM 行为影响。
- 当前自动化以 JVM 单测和 APK 静态契约为主，不替代 1240×2772 真机视觉回归。
- GitHub Actions 当前仍使用 `actions/setup-java@v4`；若 GitHub 后续停止 Node 20 action runtime，需要升级 action 版本并重新验证构建。

## 14. 相关资料

- [README](../README.md)
- [Luc MCP 连接与安全配置](luc-mcp.md)
- [Supabase API keys](https://supabase.com/docs/guides/getting-started/api-keys)
- [Supabase 新旧 API key 迁移](https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys)
- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)

