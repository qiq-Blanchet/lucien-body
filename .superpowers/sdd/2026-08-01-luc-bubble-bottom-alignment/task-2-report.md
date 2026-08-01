# Task 2 Report: Window B Bottom Alignment

## Scope

- Modified only `app/src/main/assets/css/bubble.css` for the layout behavior.
- Added the browser QA screenshot `docs/qa/luc-bubble-bottom-240x160.png`.
- Did not modify `bubble.html`, Kotlin, or `clawd_sprites/`.

## Browser RED/GREEN verification

Both checks used the desktop runtime's Playwright module with headless Microsoft Edge
(`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`). The script loaded the
real `app/src/main/assets/bubble.html` via `file:` at a `240x160` viewport, called
`window.LucBubble.show('Bottom alignment probe', 'normal', 1)`, waited 700 ms for the
existing animation, then read `getBoundingClientRect()` and `getComputedStyle(...,
'::after')`.

- RED (before CSS edit): `centerX=101.6640625`, `bottom=42.75`.
- GREEN (after CSS edit): `centerX=119.9921875`, `bottom=160`.
- GREEN arrow: `borderTopColor=rgb(143, 47, 36)`; right, bottom, and left border
  colors are `rgba(0, 0, 0, 0)`, confirming the downward arrow.

Command pattern:

```powershell
$env:NODE_PATH = 'C:\Users\Lenovo\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
@' # Playwright script: chromium.launch({ executablePath, headless: true }); page.goto(real bubble.html); LucBubble.show(); measure rect/pseudo-style
'@ | & 'C:\Users\Lenovo\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' -
```

Screenshot: `docs/qa/luc-bubble-bottom-240x160.png` (visually inspected at native
240x160 size).

## Diff and checks

- CSS diff: one new `body` rule with `display: flex`, `align-items: flex-end`,
  `justify-content: center`, and `min-height: 100vh`.
- Checks run after the edit: the GREEN browser measurement above; `git diff --check`;
  `git diff --quiet -- clawd_sprites/`.
- Commit: `Align Luc bubble at bottom of window B` (one intentional commit).
