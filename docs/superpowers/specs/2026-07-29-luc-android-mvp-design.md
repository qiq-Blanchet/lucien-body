# Luc Android MVP Design

**Date:** 2026-07-29
**Status:** Approved with review corrections
**Repository:** `qiq-Blanchet/lucien-body`

## Goal

Build a personally sideloaded Android desktop-pet APK named `Luc`. The APK is the visual and sensing body only: it renders a Clawd-inspired crab, accepts local interaction, reads state from Supabase, and reports interaction events. It contains no AI or LLM calls.

## Frozen Product Decisions

- App label: `Luc`.
- Application ID: `com.luc.body`.
- Version: `0.1.0`, version code `1`.
- Kotlin, Gradle Kotlin DSL, OkHttp, local HTML/SVG/CSS.
- `minSdk 26`, `compileSdk 36`, `targetSdk 36`, JDK 17.
- Primary acceptance device: Android 16 / API 36.
- Distribution: personal sideload, persistent release signing, no Google Play submission.
- The visible composition is `240x280dp`: a `240x160dp` bubble above a `120x120dp` pet.
- Transparent and bubble areas pass touches through; only the `120x120dp` pet hit box accepts touch.
- Local tap reactions own the visible state for 1.2 seconds. New remote state is buffered and applied afterward.
- A remote bubble replays only when `clawd_state.updated_at` changes. It fades after five seconds.
- MVP uses five-second REST polling only. Realtime is deferred.
- Existing Supabase `allow_all` RLS policy remains unchanged for MVP. Tightening backend policy is outside this implementation.

## MVP Scope

### Included

- Overlay permission guidance.
- Foreground service and persistent notification.
- Two synchronized overlay windows.
- Four SVG expressions: `idle`, `happy`, `angry`, and `sleepy`.
- CSS-driven pet and bubble animation.
- Raw-coordinate drag handling.
- Single-tap local reaction and `tap` event upload.
- Four remote bubble styles.
- Five-second Supabase state polling.
- Signed release APK built by GitHub Actions.

### Deferred

- Realtime WebSocket.
- Double tap, long press, and fling.
- Foreground-app, screenshot, battery, and charging sensing.
- Idle monologue, loneliness progression, notification monologue, and time-period behavior.
- Boot receiver and automatic startup.
- Battery allowlist guidance.
- Supabase Auth.
- AI or LLM calls.

## Architecture

```mermaid
flowchart TD
    A[MainActivity] --> S[OverlayService]
    S --> O[OverlayController]
    O --> B[Bubble WebView 240x160dp]
    O --> P[Pet WebView 120x120dp]
    P --> G[PetGestureController]
    G --> C[StateCoordinator]
    C --> P
    C --> B
    S --> N[SupabaseClient]
    N --> R[clawd_state]
    G --> E[clawd_events]
    N --> C
```

`OverlayService` is the only long-lived component. It owns the overlay controller, state coordinator, poller, and event reporter. Service shutdown cancels scheduled work, detaches both windows, and destroys both WebViews.

## Component Boundaries

### MainActivity

- Shows overlay and notification permission status.
- Opens the system overlay-permission page.
- Requests notification permission on Android 13 and later.
- Starts the foreground service only from a visible user action.
- Provides an explicit stop action.
- Does not own networking or overlay state.

### OverlayService

- Creates the notification channel and enters foreground state immediately.
- Declares Android 14+ `specialUse` foreground-service type and a persistent interactive overlay subtype description.
- Starts polling only after overlay creation succeeds.
- Uses `START_STICKY` as best-effort recovery, without promising recovery after force-stop or OEM termination.

### OverlayController

- Creates, moves, and removes both `TYPE_APPLICATION_OVERLAY` windows.
- Converts all dimensions from dp to pixels.
- Calculates display insets and safe bounds.
- Moves bubble and pet together from the pet's authoritative coordinates.
- Recomputes bounds after display-size or rotation changes.

### PetGestureController

- Consumes touch only from the `120x120dp` pet window.
- Uses `rawX` and `rawY` for movement.
- Enters drag mode after movement exceeds `10dp`.
- Emits a tap only when movement stays below `10dp` and duration stays below `200ms`.
- MVP does not emit double-tap, long-press, or fling.

### StateCoordinator

- Is the only component allowed to select the visible expression and bubble.
- Deduplicates remote state with `updated_at`.
- Owns the 1.2-second local-reaction override.
- Buffers only the newest remote state during a local override.
- Validates expression and bubble-style allowlists.
- Keeps the most recent valid state when parsing or network operations fail.

### SupabaseClient

- Uses asynchronous OkHttp calls.
- Schedules the next poll five seconds after the previous request completes, preventing overlap and catch-up bursts.
- Reads only `expression`, `bubble_text`, `bubble_style`, and `updated_at`.
- Posts `tap` events without blocking the UI.
- Retries a failed event once using the same client-generated UUID.

### WebRenderer

- Configures transparent WebViews before content loading.
- Loads only bundled content through `WebViewAssetLoader`.
- Blocks external navigation and downloads.
- Calls JavaScript with JSON-encoded arguments.
- Exposes no `JavaScriptInterface` in MVP.

## Overlay Geometry and Touch

### Pet window

- Size: `120x120dp`.
- Flags: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`.
- Pixel format: translucent.
- The whole `120x120dp` rectangle is the drag hit box, including transparent gaps within the crab SVG.

### Bubble window

- Size: `240x160dp`.
- Flags: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL`.
- It never accepts touch, including while visible.
- When no bubble is active, its DOM is hidden and the window is visually transparent.

### Placement

- Pet coordinates are authoritative.
- Bubble is normally centered above the pet.
- Bubble X is clamped independently so the pet can still reach screen edges.
- When there is not enough space above the pet, the bubble moves below it.
- Initial pet position is bottom-right with a `16dp` safe margin.
- Status bar, navigation bar, and display cutout insets are respected.
- Edge snapping is not part of MVP.

## State and Bubble Rules

### Remote state

- `idle`, `happy`, `angry`, and `sleepy` render directly.
- `shy`, `excited`, `sad`, missing expressions, and unknown expressions fall back to `idle` in MVP.
- Invalid payloads do not replace the last valid state.
- Repeated rows with the same `updated_at` do not restart animation or bubble playback.

### Local tap reaction

1. Select a local reaction immediately.
2. Display it for 1.2 seconds.
3. Post one `tap` event asynchronously.
4. Buffer only the newest remote update that arrives during the override.
5. Apply the buffered update when the override ends; otherwise restore the prior remote state.

### Bubble rendering

- Styles: `normal`, `whisper`, `shout`, and `love`.
- Null or blank text hides the bubble.
- Text is limited to 120 characters.
- DOM uses `textContent`, never `innerHTML`.
- CSS owns the five-second fade cycle.
- Only a changed `updated_at` restarts the remote bubble.

## Web Assets

```text
app/src/main/assets/
├── clawd.html
├── bubble.html
├── css/
│   ├── clawd.css
│   └── bubble.css
└── clawd_sprites/
    ├── idle.svg
    ├── happy.svg
    ├── angry.svg
    └── sleepy.svg
```

All four SVGs share the same body geometry, palette, proportions, and anchor point. Expression files change eyes, pose, tint, or attached sleep marks without changing the character identity. Animation uses CSS animation and transition rules rather than JavaScript timers.

## Supabase Contract

### State request

```text
GET /rest/v1/clawd_state
  ?select=expression,bubble_text,bubble_style,updated_at
  &order=updated_at.desc
  &limit=1
```

### Event payload

```json
{
  "id": "<client-generated-uuid>",
  "event_type": "tap",
  "payload": {}
}
```

### Headers

Per the approved project-specific behavior, MVP sends both headers:

```text
apikey: SUPABASE_PUBLISHABLE_KEY
Authorization: Bearer SUPABASE_PUBLISHABLE_KEY
Content-Type: application/json
```

A read-only live probe on 2026-07-29 returned HTTP 200 and one row for both `apikey`-only and dual-header requests. The Bearer header is therefore not required to avoid an empty array on the current project, but it is retained because the owner explicitly requires the dual-header contract and the current backend accepts it.

No project URL, key, row content, or service credential is committed.

### Current backend security state

- The existing `allow_all` RLS policy remains as-is for MVP.
- The Android implementation does not create, alter, or tighten policies.
- This is an explicitly accepted MVP risk: anyone who extracts the publishable key may have the access allowed by that policy.
- A future hardening task should replace `allow_all` with least-privilege grants and RLS, but it is not part of this build.

## Permissions and Lifecycle

Manifest permissions are limited to:

```text
android.permission.SYSTEM_ALERT_WINDOW
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
android.permission.POST_NOTIFICATIONS
android.permission.INTERNET
```

`PACKAGE_USAGE_STATS`, `RECEIVE_BOOT_COMPLETED`, storage, media, and battery permissions are not requested in MVP. `OverlayService` is not exported. Cleartext HTTP is disabled.

## Configuration and Secrets

The repository contains placeholders only. Local builds read ignored Gradle properties or environment variables. GitHub Actions reads:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Build configuration exposes the publishable key in the APK as required for a public mobile client. Security does not depend on hiding that key. No secret or service-role credential is accepted by the app.

## Repository Structure

```text
app/src/main/java/com/luc/body/
├── MainActivity.kt
├── OverlayService.kt
├── gesture/PetGestureController.kt
├── network/SupabaseClient.kt
├── overlay/OverlayController.kt
├── state/ClawdState.kt
├── state/StateCoordinator.kt
└── web/WebRenderer.kt
```

Tests mirror these responsibilities. Files are split by clear ownership and no empty second-phase classes are scaffolded.

## CI and Release

`.github/workflows/build.yml` runs on pushes to `main` and manual dispatch:

1. Check out the repository.
2. Install JDK 17.
3. Configure Gradle caching.
4. Install Android SDK 36.
5. Verify required secret names are present without printing values.
6. Decode the temporary signing keystore.
7. Run unit tests.
8. Run Android Lint.
9. Build and sign `assembleRelease`.
10. Confirm the APK exists.
11. Upload `Luc-0.1.0-release.apk` as an artifact.

Missing configuration, tests, lint, compilation, or signing failure blocks the release artifact.

## Verification

### Automated

- `10dp` movement boundary and `200ms` time boundary.
- Pet/bubble coordinate synchronization.
- Insets, edge clamping, and top-to-bottom bubble placement.
- Valid expressions and fallback behavior.
- `updated_at` deduplication.
- 1.2-second local-state priority and latest-remote buffering.
- Bubble style allowlist and text truncation.
- Invalid JSON preserves the previous state.
- Event retry reuses the same UUID.
- Poll requests never overlap.
- Web assets contain required state and bubble entry points.

### Manual Android 16 acceptance

- Signed APK installs and upgrades over the previous build.
- Permission denial does not crash or loop.
- Overlay appears above other apps.
- Bubble and transparent regions pass touches to the app below.
- Only the pet hit box accepts drag and tap.
- Dragging does not jump and both windows remain synchronized.
- Tap reaction appears immediately and produces one backend event.
- New remote state appears within one polling interval plus network latency.
- A repeated state does not replay its bubble.
- Offline and HTTP failures preserve the last valid display.
- Closing MainActivity does not remove the running pet.
- GitHub Actions publishes a signed release APK artifact.

## Implementation Coordination

The primary task acts as project manager and integration owner. Implementation is dispatched in dependency-aware tasks: project shell and service; overlay and gestures; web assets; state and network; CI and release. Each task receives an exact file scope and acceptance test. The manager reviews specification compliance and code quality before the next dependent task begins, then runs integrated verification.

## Accepted Limitations

- Android and OEM process management can still terminate a foreground service.
- Five-second polling may be delayed during Doze or severe background restrictions.
- The current `allow_all` RLS policy is intentionally not hardened in MVP.
- No local Android toolchain is currently available; GitHub Actions is the authoritative release build environment.
- Visual acceptance of the SVG pet requires a rendered review in addition to automated checks.
