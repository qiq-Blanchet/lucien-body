# Luc Bubble Bottom Alignment Design

## Goal

Keep the existing two-window Android overlay architecture while placing the bubble DOM at the bottom center of its `240x160dp` WebView so it visually meets the top of the `120x120dp` crab window.

## Approved Design

- Keep `clawd_sprites/` unchanged; the current SVG files remain placeholders for later replacement.
- Keep window A (pet) at `120x120dp` and window B (bubble) at `240x160dp`.
- Keep Android window coordinates, flags, touch behavior, and A/B synchronization unchanged.
- In `css/bubble.css`, make `body` a full-viewport flex container with `align-items: flex-end`, `justify-content: center`, and `min-height: 100vh`.
- Preserve the existing downward triangle produced by the bubble pseudo-element's top-colored border.
- Do not add dependencies or Kotlin abstractions for this CSS-only layout correction.

## Validation

- Before the change, a real browser layout check must show the visible bubble at the viewport's top/left flow origin.
- After the change, the same check must show the bubble horizontally centered and its bottom edge aligned with the `240x160` viewport bottom.
- The pseudo-element must retain a colored top border and transparent side/bottom borders, so the arrow points downward toward the crab.
- Run the focused window A/B Kotlin tests, then the full JVM suite, release lint, and APK assembly to confirm the CSS asset change does not disturb Android behavior.

## Execution Order

1. Audit window A and its gesture/geometry contracts.
2. Correct and verify window B's internal bubble placement.
3. Audit A/B synchronization and the downstream coordinator, network, and service path.
