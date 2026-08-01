# Luc 无边框气泡实施计划

## Global Constraints

- 仅修改 `app/src/main/assets/css/bubble.css` 与对应 QA 证据。
- 删除气泡基础边框、三角箭头伪元素、`shout` 边框宽度覆盖和 `love` 边框颜色覆盖。
- 将气泡文字字重从 600 改为正常字重 400，所有变体均不加粗。
- 保持底部居中布局、圆角背景、文字样式、5 秒动画和触摸穿透不变。
- 不修改 SVG、HTML、Kotlin、窗口尺寸或业务逻辑。
- 使用真实浏览器完成修改前 RED 与修改后 GREEN 验证。

## Task 1: 删除气泡箭头和边框

1. 在 240x160 浏览器视口加载实际 `bubble.html`，记录修改前边框、伪元素以及气泡中心和底边位置。
2. 从 `bubble.css` 删除 `.bubble` 的 `border` 声明和完整 `.bubble::after` 规则。
3. 删除 `.bubble--shout` 的 `border-width` 与 `.bubble--love` 的 `border-color`。
4. 验证 normal、shout、love 的边框宽度均为 0，伪元素不生成，气泡仍水平居中且贴底。
5. 保存一张更新后的 240x160 QA 截图，运行 `git diff --check`，确认无 SVG、HTML 或 Kotlin 改动并提交。

## Task 2: 取消气泡文字加粗

1. 在真实浏览器中记录 normal、whisper、shout、love 修改前的计算字重 600，作为 RED 证据。
2. 仅将 `.bubble` 的 `font` 简写字重从 600 改为 400，不改变字号、行高或字体族。
3. 验证四种气泡变体的计算字重均为 400，且无边框、无箭头、底部居中位置保持不变。
4. 更新 240x160 QA 截图，运行 `git diff --check` 并提交。
