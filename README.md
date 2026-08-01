# Luc

Luc 是一个 Android 16 桌面悬浮小宠物 MVP：它显示透明宠物与气泡，点击宠物会上报 tap 事件，并从 Supabase REST 轮询最新状态。当前范围只包含单个悬浮宠物、前台服务和 Supabase 状态/事件同步，不包含后台保活承诺、多设备账户体系或生产级 RLS 策略。

## 安装与使用

1. 在 Android 16 设备上安装 debug APK 或 GitHub Actions 产出的 release APK。
2. 打开 Luc，点击“启动”。系统会先要求允许“显示在其他应用上层”；返回应用后，Android 13 及以上还会请求通知权限。通知被拒绝时仍会继续尝试启动，但前台服务通知的可见性受系统控制。
3. 允许后，宠物和气泡会显示在其他应用上方。拖动宠物可调整位置，点击宠物会发送 tap 事件。
4. 回到 Luc 点击“停止”即可停止前台服务并移除两个悬浮窗。

> Android 的 Doze、省电策略和厂商后台管控可能终止前台服务或限制网络；这是当前 MVP 的已知限制。请在目标设备上手动验证通知、悬浮窗和重启后的行为。

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

客户端对每个 Supabase REST 请求同时发送两项 header：`apikey: <publishable key>` 与 `Authorization: Bearer <publishable key>`。后端/RLS 配置必须接受这份双 header 契约。MVP 期间若采用 `allow_all` RLS 策略，任何持有 publishable key 的客户端都可能获得过宽访问权限；上线前必须替换为最小权限的策略。

## GitHub Actions 签名发布

推送到 `main` 或手动运行 **Build release APK** 工作流会构建并上传 `Luc-0.1.0-release.apk`。在仓库 **Settings → Secrets and variables → Actions** 中配置下列六个 secrets（名称必须完全一致）：

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

工作流成功后，进入该次 Actions run，在 **Artifacts** 区下载名为 `Luc-0.1.0-release.apk` 的单文件 artifact（保留 30 天）。CI 会在上传前运行 APK 契约检查、`apksigner verify --verbose` 和 `keytool -printcert -jarfile`；没有完整签名环境变量时，本地 `assembleRelease` 可以生成 unsigned APK，但 CI 不会上传它。

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
