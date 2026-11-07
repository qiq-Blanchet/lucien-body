# Luc MCP

Luc MCP 是部署在 Supabase Edge Functions 上的远程 Streamable HTTP MCP。它使用现有 `clawd_state` 与 `clawd_events` 表，不需要额外数据库账号，也不会使用或接收 `service_role` key。

## 连接

MCP URL：

```text
https://rpfbehtavkxojipzbroi.supabase.co/functions/v1/luc-mcp
```

请求需要 Supabase **publishable key**（或兼容用的旧 anon key）。推荐在 MCP 客户端的安全凭据设置中保存，不要把真实 key 写入代码或提交到 Git：

```json
{
  "mcpServers": {
    "luc": {
      "type": "http",
      "url": "https://rpfbehtavkxojipzbroi.supabase.co/functions/v1/luc-mcp",
      "headers": {
        "Authorization": "Bearer YOUR_SUPABASE_PUBLISHABLE_KEY",
        "apikey": "YOUR_SUPABASE_PUBLISHABLE_KEY"
      }
    }
  }
}
```

服务端会拒绝 `service_role` 和 `sb_secret_...` key。publishable key 最终仍以 `anon` 数据库角色访问，因此所有能力受 GRANT 与 RLS 双重限制。

## 工具

### `luc_update`

在一次调用中更新表情、气泡文字、气泡样式和三个情绪数值，至少传一项。所有传入内容会在同一笔状态更新中生效；未传的表情和情绪值保持原值。没有提供新文字时会清掉旧气泡，避免上一句话被再次播放。

- `expression`: `idle/happy/angry/sleepy/thinking/talking/love/smug/shocked/confused/shy/proud/sulky/lonely_1/lonely_2/lonely_3/waving/peeking/morning/night/eating/dancing/dizzy/clingy/grabbed/stuck/stuck_tap/stuck_grab`
- `bubble_text`: 最多 500 个字符；空字符串用于清除气泡
- `bubble_style`: `normal/whisper/shout/love/sleepy`；有文字但不传时默认 `normal`
- `valence`: 0–1，0 更消极，1 更积极
- `arousal`: 0–1，0 更平静，1 更激动
- `heat`: 0–10 的整数，表示亲密/热情程度

一次完整更新示例：

```json
{
  "expression": "shy",
  "bubble_text": "看到你啦。",
  "bubble_style": "love",
  "valence": 0.82,
  "arousal": 0.58,
  "heat": 8
}
```

### `luc_get_status`

读取当前表情、气泡、气泡样式、`valence/arousal/heat` 与最后更新时间，无参数。

### `luc_get_events`

按从新到旧读取互动事件，不会删除或标记已读。

- `limit`: 1–100，默认 20
- `event_type`: `tap/double_tap/pet_head/drag_start/drag_end/fling/stuck_tap/app_foreground`
- `since`: 可选 ISO 8601 日期时间，只读该时间之后的事件

## 权限范围

- `clawd_state`: `SELECT`，以及仅 `expression/bubble_text/bubble_style/valence/arousal/heat` 六列的 `UPDATE`
- `clawd_events`: `SELECT`
- 不提供 `INSERT clawd_state`、`DELETE clawd_state` 或通过 MCP 删除互动事件的能力
