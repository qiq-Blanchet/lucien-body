# Luc Bubble Bottom Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bottom-center the bubble inside window B without changing the approved SVG placeholders or Android overlay architecture.

**Architecture:** Preserve the two independent overlay windows. Window A remains the authoritative touchable pet window; window B remains non-touchable and gains only CSS flex alignment inside its existing WebView.

**Tech Stack:** Android/Kotlin, WebView local assets, CSS, JUnit, local Chromium/Playwright verification, Gradle Kotlin DSL.

## Global Constraints

- App name remains `Luc`; application ID remains `com.luc.body`.
- Window A remains `120x120dp`; window B remains `240x160dp`.
- Window B remains `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`.
- Do not modify any file under `app/src/main/assets/clawd_sprites/`.
- Do not add a dependency for this CSS correction.
- Preserve all existing Supabase, coordinator, foreground-service, and signing behavior.

---

### Task 1: Audit Window A

**Files:**
- Inspect: `app/src/main/java/com/luc/body/overlay/OverlayWindowSpec.kt`
- Inspect: `app/src/main/java/com/luc/body/overlay/OverlayController.kt`
- Inspect: `app/src/main/java/com/luc/body/gesture/PetGestureController.kt`
- Test: `app/src/test/java/com/luc/body/overlay/OverlayWindowSpecTest.kt`
- Test: `app/src/test/java/com/luc/body/gesture/GestureClassifierTest.kt`

**Interfaces:**
- Consumes: approved `120x120dp` pet-window and touch contracts.
- Produces: evidence that window A needs no source change before window B is adjusted.

- [ ] **Step 1: Inspect the window A size, flags, and raw-coordinate touch path.**
- [ ] **Step 2: Run the focused overlay-window and gesture tests.**
- [ ] **Step 3: Record findings; do not edit window A when its existing contracts pass.**

### Task 2: Bottom-Align Window B Content

**Files:**
- Modify: `app/src/main/assets/css/bubble.css`
- Verify: `app/src/main/assets/bubble.html`

**Interfaces:**
- Consumes: the existing `240x160dp` non-touchable bubble WebView.
- Produces: a bubble whose DOM bottom edge meets the bottom of window B and is centered horizontally.

- [ ] **Step 1: Run the actual page in a `240x160` browser viewport and record the bubble rectangle before editing.**

Expected: the visible bubble is not bottom-centered.

- [ ] **Step 2: Add the minimum CSS implementation.**

```css
body {
  margin: 0;
  background: transparent;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  min-height: 100vh;
}
```

- [ ] **Step 3: Repeat the browser layout check.**

Expected for a `240x160` viewport: the visible bubble's horizontal center is `120px` within normal browser rounding, its bottom edge is `160px`, and the pseudo-element still forms a downward arrow.

- [ ] **Step 4: Confirm `clawd_sprites/` has no diff.**

- [ ] **Step 5: Commit the CSS correction and verification report.**

### Task 3: Audit Window B and Downstream Kotlin Flow

**Files:**
- Inspect: `app/src/main/java/com/luc/body/overlay/OverlayController.kt`
- Inspect: `app/src/main/java/com/luc/body/web/WebRenderer.kt`
- Inspect: `app/src/main/java/com/luc/body/state/StateCoordinator.kt`
- Inspect: `app/src/main/java/com/luc/body/network/PollingLoop.kt`
- Inspect: `app/src/main/java/com/luc/body/OverlayService.kt`
- Test: existing JVM test suite under `app/src/test/java/com/luc/body/`

**Interfaces:**
- Consumes: the corrected bubble asset and existing A/B overlay interfaces.
- Produces: evidence that the full Kotlin path remains complete and buildable.

- [ ] **Step 1: Verify A coordinates remain authoritative and B is positioned before A during synchronized updates.**
- [ ] **Step 2: Verify render readiness, local-first state precedence, polling, event reporting, and service cleanup remain wired.**
- [ ] **Step 3: Run `testDebugUnitTest`, `lintRelease`, `assembleDebug`, and `assembleRelease` with the existing local configuration.**
- [ ] **Step 4: Run APK contract and signature checks on the new release artifact.**
- [ ] **Step 5: Commit only if an implementation gap is found; otherwise record the audit as no Kotlin source changes required.**

