# Luc 无边框气泡实施计划

## Global Constraints

- 仅修改 `app/src/main/assets/css/bubble.css` 与对应 QA 证据。
- 删除气泡基础边框、三角箭头伪元素、`shout` 边框宽度覆盖和 `love` 边框颜色覆盖。
- 保持底部居中布局、圆角背景、文字样式、5 秒动画和触摸穿透不变。
- 不修改 SVG、HTML、Kotlin、窗口尺寸或业务逻辑。
- 使用真实浏览器完成修改前 RED 与修改后 GREEN 验证。

## Task 1: 删除气泡箭头和边框

1. 在 240x160 浏览器视口加载实际 `bubble.html`，记录修改前边框、伪元素以及气泡中心和底边位置。
2. 从 `bubble.css` 删除 `.bubble` 的 `border` 声明和完整 `.bubble::after` 规则。
3. 删除 `.bubble--shout` 的 `border-width` 与 `.bubble--love` 的 `border-color`。
4. 验证 normal、shout、love 的边框宽度均为 0，伪元素不生成，气泡仍水平居中且贴底。
5. 保存一张更新后的 240x160 QA 截图，运行 `git diff --check`，确认无 SVG、HTML 或 Kotlin 改动并提交。
