# Luc

Luc 是一个面向 Android 8.0–16 的桌面悬浮宠物。应用 ID 为 `com.luc.body`，使用双悬浮层分别承载可交互的 Clawd 和全触摸穿透的文字气泡；APK 内只负责“身体”，远端状态通过 Supabase 下发，不在设备内调用 AI/LLM。

V2 已实现 28 个具名状态、37 份 SVG 变体随机池、外部素材覆盖、完整手势、时段/孤独/自言自语/前台 App 感知、Realtime 主通道与 HTTP 轮询降级。37 份内置素材中，35 份逐字节对应清单中的 GitHub Blob，`angry.svg` 与 `sleepy.svg` 是素材板明确标注的自定义原件；第 26 张 `clawd-collapse-sleep.svg` 已按上游原件纳入为 `night.svg`。

## 功能

- 90×90dp 默认宠物层、180×120dp 气泡层；宠物大小可设为 60–120dp。
- raw 坐标拖拽、10dp 阈值、300ms 双击、500ms 长按菜单、四向 fling 与贴边吸附。
- tap/双击/吸附点击 1.2 秒本地锁；拖拽不可被远端状态打断。
- Supabase Realtime UPDATE、25 秒心跳、指数退避重连、失败后 5 秒 HTTP 轮询。
- Supabase 下发的 expression 与同条气泡同时到期；无气泡时按当前基础气泡时长到期，再回到吸附、时段/孤独或 idle。
- 事件满 3 条或 10 秒批量上报；REST 与 WebSocket 均携带 `apikey` 和 Bearer。
- 开机自启、常驻通知“显示/隐藏”“退出”、位置恢复以及 10 项设置。

## 安装与使用

1. 安装 GitHub Actions 产出的 `Luc-0.1.6-release.apk`。
2. 首次打开时按说明依次处理悬浮窗权限、电池优化白名单和使用情况访问；只有悬浮窗权限是运行必需，后两项可跳过。Android 13 及以上在启动时另行请求通知权限。
3. Android 16 从下载的 APK 安装时，会把悬浮窗和使用情况访问列为受限设置。若系统开关不可点，先在 Luc 主界面点“打开 Luc 应用详情”，再点应用详情右上角菜单中的“允许受限设置”；返回后用主界面的两个权限按钮分别授权。
4. 填写 Supabase URL 与 publishable key，保存设置后点击“启动 Luc”。留空时本地手势与行为仍可运行，但不会连接远端。
5. 单击、双击、长按或甩动宠物进行互动；长按菜单可戳一下、摸摸头、隐藏或打开设置。隐藏后可从常驻通知恢复。
6. “停止 Luc”或通知中的“退出”会停止前台服务并移除两个悬浮层。

> Android 的 Doze 和厂商后台管控仍可能限制网络或终止进程；电池白名单是建议项，不是规避系统规则的保证。

## 权限

| 权限/设置 | 用途 | 必需性 |
| --- | --- | --- |
| 显示在其他应用上层 | 创建宠物和气泡悬浮层 | 必需 |
| 通知 | 显示前台服务状态和操作按钮 | Android 13+ 建议 |
| 忽略电池优化 | 降低后台连接被中断的概率 | 可选 |
| 使用情况访问 | 每 5 秒识别前台 App，用于 idle/peeking/waving | 可选 |
| 开机完成广播 | 按设置自动恢复桌宠 | 默认开启，可关闭 |

## 本地配置与构建

需要 JDK 17、Android SDK Platform 36 与 Build Tools 36.0.0。`local.properties` 已被 `.gitignore` 忽略；可保留本机 SDK 路径及以下**占位**记录，绝不要提交：

```properties
sdk.dir=YOUR_ANDROID_SDK_PATH
SUPABASE_URL=YOUR_SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

构建脚本会从 Gradle `-P` 属性优先、再从环境变量、最后从 `local.properties` 读取 Supabase 配置。若不希望把值保存在文件中，也可在 PowerShell 中设置本次会话环境变量：

```powershell
$env:SUPABASE_URL = 'YOUR_SUPABASE_URL'
$env:SUPABASE_PUBLISHABLE_KEY = 'YOUR_SUPABASE_PUBLISHABLE_KEY'
.\gradlew.bat --no-daemon assembleDebug
```

客户端对每个 Supabase REST 请求同时发送两项 header：`apikey: <publishable key>` 与 `Authorization: Bearer <publishable key>`。当前 MVP 后端若使用 `allow_all` RLS，任何持有 publishable key 的客户端都可能获得过宽访问权限；公开发布前应替换为最小权限策略。本项目不包含数据库迁移，也不会修改现有表或 RLS。

## GitHub Actions 签名发布

推送到 `main` 或手动运行 **Build release APK** 工作流会构建并上传签名的 `Luc-0.1.6-release.apk`；针对 `main` 的 pull request 会运行单测、lint 和 debug 构建。在仓库 **Settings → Secrets and variables → Actions** 中配置下列六个 secrets（名称必须完全一致）：

| Secret | 用途 |
| --- | --- |
| `SUPABASE_URL` | Supabase 项目 URL |
| `SUPABASE_PUBLISHABLE_KEY` | Supabase publishable key |
| `ANDROID_KEYSTORE_BASE64` | release keystore 的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | release key alias |
| `ANDROID_KEY_PASSWORD` | alias 对应的 key 密码 |

工作流在启动 Gradle 前检查六项是否均非空；失败日志只会列出缺失的变量名。keystore 会仅解码到 runner 的临时目录，不写入仓库。

首次创建个人 keystore 时，选择受控且会备份的位置，并让 `keytool` 交互式输入密码（不要把密码放进命令历史）：

```powershell
keytool -genkeypair -v -keystore C:\secure\luc-release.jks -alias luc-release -keyalg RSA -keysize 4096 -validity 10000
```

将 keystore 加入离线、加密备份，并长期保留同一份文件、alias 和密码。**覆盖安装/升级必须使用同一 keystore；丢失它将无法更新已安装的 release APK。**

以下 PowerShell 命令只把 Base64 放入剪贴板，不在终端回显密码或内容；随后直接粘贴到 `ANDROID_KEYSTORE_BASE64` secret，并按需清空剪贴板：

```powershell
$encoded = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes('C:\secure\luc-release.jks'))
Set-Clipboard -Value $encoded
Remove-Variable encoded
```

工作流成功后，进入该次 Actions run，在 **Artifacts** 区下载名为 `Luc-0.1.6-release.apk` 的单文件 artifact（保留 30 天）。CI 会在上传前运行 APK 契约检查、`apksigner verify --verbose` 和 `keytool -printcert -jarfile`；没有完整签名环境变量时，本地 `assembleRelease` 可以生成 unsigned APK，但 CI 不会上传它。

## 验证命令

本地源码、lint 与 debug APK 验证：

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleDebug
```

在 Git Bash、WSL 或 Ubuntu 中对真实 APK 运行黑盒契约验证：

```bash
./scripts/verify-apk-contract.sh app/build/outputs/apk/debug/app-debug.apk
```

CI 还会以固定的 `actionlint` 版本检查 `.github/workflows/build.yml`，并使用 Android SDK Build Tools 36.0.0 验证 release 签名。
