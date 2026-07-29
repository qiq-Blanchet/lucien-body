# Luc Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `Luc` Android 16 desktop-pet source project and a GitHub Actions pipeline that produces a signed, personally sideloadable release APK.

**Architecture:** One foreground `OverlayService` owns two synchronized `TYPE_APPLICATION_OVERLAY` windows: a non-touchable bubble WebView and a touchable pet WebView. Pure Kotlin state, gesture, and geometry units isolate behavior from Android framework adapters; an asynchronous OkHttp client polls Supabase and reports tap events.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17, Android SDK 36, AndroidX Core 1.19.0, Activity 1.12.3, WebKit 1.16.0, OkHttp/MockWebServer 5.3.0, JUnit 4.13.2.

## Global Constraints

- App label is exactly `Luc`.
- Application ID and namespace are exactly `com.luc.body`.
- Version is `versionName = "0.1.0"` and `versionCode = 1`.
- `minSdk = 26`, `compileSdk = 36`, and `targetSdk = 36`.
- Use Kotlin, Gradle Kotlin DSL, JDK 17, OkHttp, and local HTML/SVG/CSS.
- The visible composition is a `240x160dp` non-touchable bubble window plus a `120x120dp` touchable pet window.
- Only the pet window receives touch. The bubble and all area outside the pet window pass through.
- Use `rawX` and `rawY`; movement below `10dp` and duration below `200ms` is a tap.
- Local tap reactions own the UI for 1,200 ms; only the newest remote update is applied afterward.
- Deduplicate remote state and bubble playback by `updated_at`; a bubble fades after five seconds.
- MVP supports only `idle`, `happy`, `angry`, and `sleepy`; every other expression falls back to `idle`.
- MVP implements REST polling every five seconds and `tap` POST only. Do not add Realtime or phase-two sensing.
- Supabase requests send both `apikey` and `Authorization: Bearer <publishable key>` using the same configured publishable key.
- Preserve the existing Supabase `allow_all` policy. Do not change database schema, grants, or RLS.
- Never commit, print, or snapshot real Supabase values, signing material, passwords, or tokens.
- GitHub Actions on `main` is the authoritative release build and must upload a signed APK.

---

## File Map

```text
.
├── .github/workflows/build.yml
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.jar
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   ├── bubble.html
        │   │   ├── clawd.html
        │   │   ├── css/bubble.css
        │   │   ├── css/clawd.css
        │   │   └── clawd_sprites/{idle,happy,angry,sleepy}.svg
        │   ├── java/com/luc/body/
        │   │   ├── AppIdentity.kt
        │   │   ├── MainActivity.kt
        │   │   ├── OverlayService.kt
        │   │   ├── gesture/{GestureClassifier,PetGestureController}.kt
        │   │   ├── network/{PollingLoop,SupabaseClient,SupabaseConfig}.kt
        │   │   ├── overlay/{OverlayController,OverlayGeometry,OverlayWindowSpec}.kt
        │   │   ├── state/{ClawdState,DelayScheduler,StateCoordinator}.kt
        │   │   └── web/WebRenderer.kt
        │   └── res/
        │       ├── drawable/ic_notification.xml
        │       ├── layout/activity_main.xml
        │       ├── values/{colors,strings,themes}.xml
        │       └── values-night/themes.xml
        └── test/java/com/luc/body/
            ├── AppIdentityTest.kt
            ├── RepositoryContractTest.kt
            ├── gesture/GestureClassifierTest.kt
            ├── network/{PollingLoop,SupabaseClientTest}.kt
            ├── overlay/{OverlayGeometryTest,OverlayWindowSpecTest}.kt
            ├── state/StateCoordinatorTest.kt
            └── web/WebAssetContractTest.kt
```

### Task 1: Buildable Android 16 Project Foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `.gitignore`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/luc/body/AppIdentity.kt`
- Create: `app/src/main/java/com/luc/body/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Test: `app/src/test/java/com/luc/body/AppIdentityTest.kt`

**Interfaces:**
- Consumes: No production interfaces; this task establishes the build.
- Produces: `AppIdentity.label: String`, `AppIdentity.applicationId: String`, `AppIdentity.versionName: String`, generated `BuildConfig.SUPABASE_URL`, and generated `BuildConfig.SUPABASE_PUBLISHABLE_KEY`.

- [ ] **Step 1: Add the Gradle wrapper and pinned version catalog**

Use Gradle 8.13 wrapper files. `gradle/libs.versions.toml` must contain:

```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.21"
core = "1.19.0"
activity = "1.12.3"
webkit = "1.16.0"
okhttp = "5.3.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core" }
androidx-activity-ktx = { module = "androidx.activity:activity-ktx", version.ref = "activity" }
androidx-webkit = { module = "androidx.webkit:webkit", version.ref = "webkit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

Use `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip` and verify the wrapper JAR comes from the official Gradle 8.13 distribution.

- [ ] **Step 2: Add a failing identity test**

```kotlin
package com.luc.body

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun exposesFrozenApplicationIdentity() {
        assertEquals("Luc", AppIdentity.label)
        assertEquals("com.luc.body", AppIdentity.applicationId)
        assertEquals("0.1.0", AppIdentity.versionName)
    }
}
```

- [ ] **Step 3: Run the test and confirm the missing production type**

Run: `./gradlew :app:testDebugUnitTest --tests com.luc.body.AppIdentityTest`

Expected: FAIL because `AppIdentity` does not exist.

- [ ] **Step 4: Configure the Android app and implement the identity**

Root plugin file:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

App build essentials:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun configValue(name: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse("")
        .get()

android {
    namespace = "com.luc.body"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luc.body"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${configValue("SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${configValue("SUPABASE_PUBLISHABLE_KEY")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
```

Production identity:

```kotlin
package com.luc.body

object AppIdentity {
    const val label = "Luc"
    const val applicationId = "com.luc.body"
    const val versionName = "0.1.0"
}
```

Create a minimal `ComponentActivity` that inflates `activity_main.xml`; the layout contains a title, permission status text, start button, and stop button with string resources only.

- [ ] **Step 5: Add secure ignore rules**

`.gitignore` must exclude:

```gitignore
.gradle/
.idea/
local.properties
*.iml
**/build/
*.jks
*.keystore
keystore.properties
signing/
```

- [ ] **Step 6: Verify the project foundation**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit**

```bash
git add .gitignore build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: scaffold Luc Android 16 app"
```

### Task 2: State Model and Local-Override Coordinator

**Files:**
- Create: `app/src/main/java/com/luc/body/state/ClawdState.kt`
- Create: `app/src/main/java/com/luc/body/state/DelayScheduler.kt`
- Create: `app/src/main/java/com/luc/body/state/StateCoordinator.kt`
- Test: `app/src/test/java/com/luc/body/state/StateCoordinatorTest.kt`

**Interfaces:**
- Consumes: No Android framework types.
- Produces:
  - `enum class Expression`
  - `enum class BubbleStyle`
  - `data class RemoteState`
  - `data class VisibleState`
  - `fun interface UiSink { fun render(state: VisibleState) }`
  - `interface DelayScheduler`
  - `class StateCoordinator`

- [ ] **Step 1: Write failing coordinator tests**

Tests must cover remote dedupe and latest-remote buffering:

```kotlin
@Test
fun duplicateUpdatedAtDoesNotRenderTwice() {
    val sink = RecordingSink()
    val coordinator = StateCoordinator(sink, FakeScheduler()) { Expression.HAPPY }
    val state = RemoteState(Expression.ANGRY, "Hey", BubbleStyle.SHOUT, "2026-07-29T12:00:00Z")

    coordinator.onRemoteState(state)
    coordinator.onRemoteState(state)

    assertEquals(1, sink.states.size)
}

@Test
fun newestRemoteStateWinsAfterLocalOverride() {
    val sink = RecordingSink()
    val scheduler = FakeScheduler()
    val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
    coordinator.onRemoteState(RemoteState(Expression.IDLE, null, BubbleStyle.NORMAL, "1"))

    coordinator.onLocalTap()
    coordinator.onRemoteState(RemoteState(Expression.ANGRY, "A", BubbleStyle.SHOUT, "2"))
    coordinator.onRemoteState(RemoteState(Expression.SLEEPY, "B", BubbleStyle.WHISPER, "3"))
    scheduler.advanceBy(1_200)

    assertEquals(Expression.SLEEPY, sink.states.last().expression)
    assertEquals("B", sink.states.last().bubbleText)
}
```

Also test `shy`, `excited`, `sad`, and arbitrary strings map to `IDLE`; blank bubble text maps to null; text is truncated to 120 characters.

- [ ] **Step 2: Run the tests and confirm missing state types**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luc.body.state.*"`

Expected: FAIL with unresolved `StateCoordinator`, `RemoteState`, and related types.

- [ ] **Step 3: Implement the state contracts**

```kotlin
enum class Expression {
    IDLE, HAPPY, ANGRY, SLEEPY;

    companion object {
        fun fromRemote(value: String?): Expression = when (value?.lowercase()) {
            "happy" -> HAPPY
            "angry" -> ANGRY
            "sleepy" -> SLEEPY
            else -> IDLE
        }
    }
}

enum class BubbleStyle {
    NORMAL, WHISPER, SHOUT, LOVE;

    companion object {
        fun fromRemote(value: String?): BubbleStyle = when (value?.lowercase()) {
            "whisper" -> WHISPER
            "shout" -> SHOUT
            "love" -> LOVE
            else -> NORMAL
        }
    }
}

data class RemoteState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val updatedAt: String,
)

data class VisibleState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val revision: String,
)

fun interface UiSink {
    fun render(state: VisibleState)
}
```

Scheduler contracts:

```kotlin
fun interface Cancelable {
    fun cancel()
}

fun interface DelayScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): Cancelable
}
```

- [ ] **Step 4: Implement coordinator precedence**

`StateCoordinator.onLocalTap()` must cancel a prior local timeout, render a local reaction with revision `local-<counter>`, schedule exactly 1,200 ms, and apply the newest buffered remote state at expiry. `onRemoteState()` must ignore repeated `updatedAt`. `close()` cancels the active timeout.

- [ ] **Step 5: Run coordinator tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luc.body.state.*"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luc/body/state app/src/test/java/com/luc/body/state
git commit -m "feat: coordinate remote and local pet state"
```

### Task 3: Gesture Classification and Overlay Geometry

**Files:**
- Create: `app/src/main/java/com/luc/body/gesture/GestureClassifier.kt`
- Create: `app/src/main/java/com/luc/body/gesture/PetGestureController.kt`
- Create: `app/src/main/java/com/luc/body/overlay/OverlayGeometry.kt`
- Create: `app/src/main/java/com/luc/body/overlay/OverlayWindowSpec.kt`
- Test: `app/src/test/java/com/luc/body/gesture/GestureClassifierTest.kt`
- Test: `app/src/test/java/com/luc/body/overlay/OverlayGeometryTest.kt`
- Test: `app/src/test/java/com/luc/body/overlay/OverlayWindowSpecTest.kt`

**Interfaces:**
- Consumes: Density supplied as `Float`; no service types.
- Produces:
  - `GestureClassifier.onDown/onMove/onUp`
  - `sealed interface GestureResult`
  - `data class SafeBoundsPx`
  - `data class OverlayPlacementPx`
  - `OverlayGeometry.initialPlacement/movePet`
  - `OverlayWindowSpec.pet/bubble`
  - `PetGestureController(onMove, onTap)`

- [ ] **Step 1: Write failing boundary tests**

```kotlin
@Test
fun movementBelowTenDpWithinTwoHundredMsIsTap() {
    val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
    classifier.onDown(100f, 100f, 1_000)
    val result = classifier.onUp(119f, 100f, 1_199)
    assertEquals(GestureResult.Tap, result)
}

@Test
fun movementAtThresholdIsNotTap() {
    val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
    classifier.onDown(100f, 100f, 1_000)
    val result = classifier.onUp(120f, 100f, 1_100)
    assertEquals(GestureResult.DragEnd, result)
}
```

Geometry tests must assert:

```kotlin
@Test
fun bubbleMovesBelowPetWhenTopSpaceIsInsufficient() {
    val geometry = OverlayGeometry(density = 2f)
    val bounds = SafeBoundsPx(0, 40, 1080, 2200)
    val placement = geometry.movePet(500, 60, bounds)
    assertTrue(placement.bubbleBelowPet)
    assertEquals(60 + 240, placement.bubbleY)
}
```

Add tests for bottom-right initial placement with `16dp` margin, independent bubble X clamping, and pet/bubble synchronization.

- [ ] **Step 2: Run the tests and confirm missing classifiers**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.luc.body.gesture.*" --tests "com.luc.body.overlay.*"
```

Expected: FAIL because classifiers and geometry types do not exist.

- [ ] **Step 3: Implement pure gesture classification**

```kotlin
sealed interface GestureResult {
    data object None : GestureResult
    data class Move(val deltaX: Float, val deltaY: Float) : GestureResult
    data object Tap : GestureResult
    data object DragEnd : GestureResult
}
```

Use Euclidean distance from the original down point. The exact threshold is exclusive for taps: `distance < touchSlopPx` and `duration < tapTimeoutMs`.

- [ ] **Step 4: Implement overlay geometry**

Constants are exact:

```kotlin
private const val PET_SIZE_DP = 120
private const val BUBBLE_WIDTH_DP = 240
private const val BUBBLE_HEIGHT_DP = 160
private const val EDGE_MARGIN_DP = 16
```

`movePet()` clamps the pet to `SafeBoundsPx`. It centers the bubble above, clamps bubble X independently, and places the bubble below when `petY - bubbleHeight < bounds.top`.

- [ ] **Step 5: Implement the Android touch adapter**

`PetGestureController` receives a `GestureClassifier`, `onMove(deltaX, deltaY)`, and `onTap()`. Its `onTouch(view, event)` passes `event.rawX`, `event.rawY`, and `event.eventTime`, consumes the event, and never uses view-relative coordinates.

```kotlin
class PetGestureController(
    private val classifier: GestureClassifier,
    private val onMove: (Float, Float) -> Unit,
    private val onTap: () -> Unit,
) : View.OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val result = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                classifier.onDown(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_MOVE ->
                classifier.onMove(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                classifier.onUp(event.rawX, event.rawY, event.eventTime)
            else -> GestureResult.None
        }
        when (result) {
            is GestureResult.Move -> onMove(result.deltaX, result.deltaY)
            GestureResult.Tap -> onTap()
            GestureResult.None, GestureResult.DragEnd -> Unit
        }
        return true
    }
}
```

- [ ] **Step 6: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.luc.body.gesture.*" --tests "com.luc.body.overlay.*" :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luc/body/gesture app/src/main/java/com/luc/body/overlay app/src/test/java/com/luc/body/gesture app/src/test/java/com/luc/body/overlay
git commit -m "feat: add pet gesture and overlay geometry"
```

### Task 4: Clawd SVG, Bubble UI, and Secure Web Rendering

**Files:**
- Create: `app/src/main/assets/clawd.html`
- Create: `app/src/main/assets/bubble.html`
- Create: `app/src/main/assets/css/clawd.css`
- Create: `app/src/main/assets/css/bubble.css`
- Create: `app/src/main/assets/clawd_sprites/idle.svg`
- Create: `app/src/main/assets/clawd_sprites/happy.svg`
- Create: `app/src/main/assets/clawd_sprites/angry.svg`
- Create: `app/src/main/assets/clawd_sprites/sleepy.svg`
- Create: `app/src/main/java/com/luc/body/web/WebRenderer.kt`
- Test: `app/src/test/java/com/luc/body/web/WebAssetContractTest.kt`

**Interfaces:**
- Consumes: `UiSink`, `VisibleState`, `Expression`, `BubbleStyle`.
- Produces: `class WebRenderer(petWebView: WebView, bubbleWebView: WebView) : UiSink`.

- [ ] **Step 1: Write a failing asset contract test**

```kotlin
@Test
fun requiredWebAssetsExposeSafeEntryPoints() {
    val assets = locateAssetsDirectory()
    val pet = assets.resolve("clawd.html").readText()
    val bubble = assets.resolve("bubble.html").readText()

    assertTrue(pet.contains("window.LucPet.setExpression"))
    assertTrue(bubble.contains("window.LucBubble.show"))
    assertTrue(bubble.contains("textContent"))
    assertFalse(bubble.contains("innerHTML"))
    listOf("idle", "happy", "angry", "sleepy").forEach {
        assertTrue(assets.resolve("clawd_sprites/$it.svg").isFile)
    }
}
```

`locateAssetsDirectory()` checks `app/src/main/assets` and `src/main/assets`, selecting the first existing directory.

- [ ] **Step 2: Run the contract test**

Run: `./gradlew :app:testDebugUnitTest --tests com.luc.body.web.WebAssetContractTest`

Expected: FAIL because the assets do not exist.

- [ ] **Step 3: Create the four consistent SVG states**

Each SVG uses `viewBox="0 0 120 120"`, the same orange-red body, two claws, two eyes, and the same baseline. Do not use external images, fonts, scripts, shadows, or network URLs. `sleepy.svg` may include attached `z` marks inside the viewBox.

The shared geometry starts from this exact idle body and keeps the body, claw, and eye anchor coordinates in every state:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" role="img" aria-label="Luc idle">
  <g fill="#E7653E" stroke="#8F2F24" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">
    <ellipse cx="60" cy="70" rx="31" ry="25"/>
    <path d="M34 62C19 57 11 45 18 36c8-10 20-1 19 10"/>
    <path d="M86 62c15-5 23-17 16-26-8-10-20-1-19 10"/>
    <path d="M39 89l-14 13M53 94l-5 16M67 94l5 16M81 89l14 13"/>
  </g>
  <g fill="#FFF8EC" stroke="#8F2F24" stroke-width="4">
    <circle cx="47" cy="50" r="10"/><circle cx="73" cy="50" r="10"/>
  </g>
  <g fill="#2F211D"><circle cx="49" cy="52" r="4"/><circle cx="71" cy="52" r="4"/></g>
  <path d="M51 76q9 7 18 0" fill="none" stroke="#8F2F24" stroke-width="4" stroke-linecap="round"/>
</svg>
```

`happy.svg` replaces the pupils with upward eye arcs and widens the smile; `angry.svg` replaces each pupil with two crossing strokes and turns the mouth downward; `sleepy.svg` replaces eyes with horizontal closed-eye arcs, uses a small neutral mouth, and adds three attached `z` glyph paths between x=85..110 and y=20..55. No state moves the body ellipse or leg endpoints.

- [ ] **Step 4: Implement the pet page and CSS-only motion**

`clawd.html` loads one sprite at a time and exposes:

```javascript
window.LucPet = {
  setExpression(name) {
    const allowed = new Set(["idle", "happy", "angry", "sleepy"]);
    const safe = allowed.has(name) ? name : "idle";
    const pet = document.getElementById("pet");
    pet.className = `pet pet--${safe}`;
    pet.src = `clawd_sprites/${safe}.svg`;
  }
};
```

CSS defines idle float/blink, happy sway, angry shake, and sleepy breathe animation. JavaScript does not use `setInterval` or `setTimeout`.

- [ ] **Step 5: Implement the bubble page**

```javascript
window.LucBubble = {
  show(text, style, revision) {
    const allowed = new Set(["normal", "whisper", "shout", "love"]);
    const bubble = document.getElementById("bubble");
    bubble.textContent = String(text).slice(0, 120);
    bubble.className = `bubble bubble--${allowed.has(style) ? style : "normal"}`;
    bubble.dataset.revision = revision;
    void bubble.offsetWidth;
    bubble.classList.add("bubble--visible");
  },
  hide() {
    document.getElementById("bubble").className = "bubble";
  }
};
```

`bubble--visible` runs one five-second CSS keyframe: fade in, hold, fade out. Body and HTML backgrounds are transparent.

- [ ] **Step 6: Implement secure WebView configuration**

`WebRenderer` must:

- call `setBackgroundColor(Color.TRANSPARENT)` before loading;
- enable JavaScript;
- disable file access, content access, and file-URL cross-origin access;
- use `WebViewAssetLoader` at `https://appassets.androidplatform.net/assets/`;
- reject external navigation in `WebViewClient`;
- never add a JavaScript interface;
- use `JSONObject.quote()` for every string passed to `evaluateJavascript`.

`render()` invokes `LucPet.setExpression`; it invokes `LucBubble.hide` for null text and `LucBubble.show` otherwise.

- [ ] **Step 7: Run web tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.luc.body.web.WebAssetContractTest :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 8: Render and visually inspect assets**

Open `clawd.html` and `bubble.html` using a local browser or Android WebView preview. Confirm identical crab identity, transparent backgrounds, no clipping, all four distinguishable states, and a complete five-second bubble cycle. Save one contact-sheet screenshot under `docs/qa/luc-mvp-svg-contact-sheet.png`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/assets app/src/main/java/com/luc/body/web app/src/test/java/com/luc/body/web docs/qa/luc-mvp-svg-contact-sheet.png
git commit -m "feat: render Luc pet and bubble assets"
```

### Task 5: Supabase REST Client and Non-Overlapping Polling

**Files:**
- Create: `app/src/main/java/com/luc/body/network/SupabaseConfig.kt`
- Create: `app/src/main/java/com/luc/body/network/SupabaseClient.kt`
- Create: `app/src/main/java/com/luc/body/network/PollingLoop.kt`
- Test: `app/src/test/java/com/luc/body/network/SupabaseClientTest.kt`
- Test: `app/src/test/java/com/luc/body/network/PollingLoopTest.kt`

**Interfaces:**
- Consumes: `RemoteState`, `Expression.fromRemote`, `BubbleStyle.fromRemote`, `DelayScheduler`.
- Produces:
  - `data class SupabaseConfig`
  - `SupabaseClient.fetchLatest(callback): Call`
  - `SupabaseClient.postTap(eventId, callback): Call`
  - `PollingLoop.start/stop`

- [ ] **Step 1: Write failing MockWebServer tests**

```kotlin
@Test
fun fetchUsesDualHeadersAndParsesLatestState() {
    server.enqueue(
        MockResponse.Builder()
            .code(200)
            .body("""[{"expression":"happy","bubble_text":"在呢","bubble_style":"love","updated_at":"42"}]""")
            .build(),
    )
    val result = awaitResult { client.fetchLatest(it) }
    val request = server.takeRequest()

    assertEquals("test-key", request.headers["apikey"])
    assertEquals("Bearer test-key", request.headers["Authorization"])
    assertEquals(Expression.HAPPY, result.getOrThrow()?.expression)
}
```

Add tests for:

- empty arrays returning success with null;
- invalid JSON returning failure;
- unknown expression falling back to idle;
- POST JSON containing one generated UUID and `event_type = tap`;
- a 503 response causing exactly one POST retry with the same UUID;
- GET failure not immediately retrying.

- [ ] **Step 2: Run network tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luc.body.network.*"`

Expected: FAIL because the network client does not exist.

- [ ] **Step 3: Implement validated configuration**

```kotlin
data class SupabaseConfig(
    val baseUrl: String,
    val publishableKey: String,
) {
    fun requireValid(): SupabaseConfig {
        require(baseUrl.startsWith("https://") && baseUrl.endsWith(".supabase.co"))
        require(publishableKey.startsWith("sb_publishable_"))
        return this
    }
}
```

Do not include configuration values in exception messages.

- [ ] **Step 4: Implement the REST client**

Build the GET URL with `HttpUrl.Builder`. Parse the first JSON array object with `org.json`. Every request sets:

```kotlin
.header("apikey", config.publishableKey)
.header("Authorization", "Bearer ${config.publishableKey}")
.header("Accept", "application/json")
```

POST also sets `Content-Type: application/json` and `Prefer: return=minimal`. Retry POST at most once on transport failure or non-2xx response; reuse the exact event body and UUID.

- [ ] **Step 5: Implement completion-driven polling**

`PollingLoop.start()` issues one fetch. Its callback sends a non-null state to `onState` and schedules the next fetch 5,000 ms later regardless of success. `start()` is idempotent. `stop()` cancels the scheduled task and active call. It never schedules fixed-rate catch-up work.

- [ ] **Step 6: Run network tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luc.body.network.*"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luc/body/network app/src/test/java/com/luc/body/network
git commit -m "feat: connect Luc to Supabase REST"
```

### Task 6: Two-Window Overlay Controller

**Files:**
- Create: `app/src/main/java/com/luc/body/overlay/OverlayController.kt`
- Modify: `app/src/main/java/com/luc/body/overlay/OverlayWindowSpec.kt`
- Modify: `app/src/main/java/com/luc/body/gesture/PetGestureController.kt`
- Test: `app/src/test/java/com/luc/body/overlay/OverlayWindowSpecTest.kt`

**Interfaces:**
- Consumes: `OverlayGeometry`, `OverlayWindowSpec`, `PetGestureController`, `WebRenderer`, and `UiSink`.
- Produces:
  - `OverlayController.show()`
  - `OverlayController.moveBy(deltaX, deltaY)`
  - `OverlayController.render(state)`
  - `OverlayController.remove()`

- [ ] **Step 1: Add failing exact-window-spec tests**

```kotlin
@Test
fun bubbleWindowIsNeverTouchable() {
    val bubble = OverlayWindowSpec.bubble()
    assertEquals(240, bubble.widthDp)
    assertEquals(160, bubble.heightDp)
    assertFalse(bubble.touchable)
}

@Test
fun petWindowIsExactlyOneHundredTwentyDpAndTouchable() {
    val pet = OverlayWindowSpec.pet()
    assertEquals(120, pet.widthDp)
    assertEquals(120, pet.heightDp)
    assertTrue(pet.touchable)
}
```

- [ ] **Step 2: Run the spec test**

Run: `./gradlew :app:testDebugUnitTest --tests com.luc.body.overlay.OverlayWindowSpecTest`

Expected: FAIL until exact specs are exposed.

- [ ] **Step 3: Implement exact specs and LayoutParams mapping**

Pet flags:

```kotlin
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
```

Bubble flags:

```kotlin
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
```

Both use `TYPE_APPLICATION_OVERLAY`, `PixelFormat.TRANSLUCENT`, and `Gravity.TOP or Gravity.START`.

- [ ] **Step 4: Implement controller lifecycle**

`show()` creates two WebViews, configures `WebRenderer`, calculates initial placement, adds bubble then pet, and attaches touch only to the pet. `moveBy()` derives new pet coordinates from the drag-start window coordinates and asks `OverlayGeometry` for both placements. `remove()` is idempotent, removes both views if attached, clears touch listeners, stops WebView loading, and destroys both WebViews.

```kotlin
class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val geometry: OverlayGeometry,
    private val onTap: () -> Unit,
) : UiSink {
    fun show()
    fun moveBy(deltaX: Float, deltaY: Float)
    override fun render(state: VisibleState)
    fun remove()
}
```

Store `petParams` and `bubbleParams` separately. Every move writes all four coordinates, then calls `updateViewLayout(bubbleView, bubbleParams)` followed by `updateViewLayout(petView, petParams)`. Catch `IllegalArgumentException` only during idempotent removal; do not swallow add or update failures.

- [ ] **Step 5: Compile and run all pure overlay tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.luc.body.overlay.*" --tests "com.luc.body.gesture.*" :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luc/body/overlay app/src/main/java/com/luc/body/gesture app/src/test/java/com/luc/body/overlay
git commit -m "feat: add synchronized Luc overlay windows"
```

### Task 7: Permission UI and Foreground Service Integration

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/luc/body/MainActivity.kt`
- Create: `app/src/main/java/com/luc/body/OverlayService.kt`
- Create: `app/src/main/res/drawable/ic_notification.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/luc/body/RepositoryContractTest.kt`

**Interfaces:**
- Consumes: `OverlayController`, `StateCoordinator`, `SupabaseClient`, `PollingLoop`, `BuildConfig` values.
- Produces: complete app start/stop lifecycle and notification behavior.

- [ ] **Step 1: Write a failing manifest contract test**

```kotlin
@Test
fun manifestDeclaresOnlyMvpSpecialPermissionsAndSpecialUseService() {
    val manifest = locateRepositoryFile("app/src/main/AndroidManifest.xml").readText()
    assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
    assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse\""))
    assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
    assertFalse(manifest.contains("PACKAGE_USAGE_STATS"))
    assertFalse(manifest.contains("RECEIVE_BOOT_COMPLETED"))
}
```

Also assert the service is `android:exported="false"` and cleartext traffic is disabled.

- [ ] **Step 2: Run the repository contract**

Run: `./gradlew :app:testDebugUnitTest --tests com.luc.body.RepositoryContractTest`

Expected: FAIL until the final manifest contract exists.

- [ ] **Step 3: Implement the manifest**

Declare exactly:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

Service:

```xml
<service
    android:name=".OverlayService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-started persistent interactive desktop pet overlay" />
</service>
```

- [ ] **Step 4: Implement MainActivity permission flow**

On resume, update visible permission status. The start button:

1. opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` when overlay access is absent;
2. requests `POST_NOTIFICATIONS` on API 33+ when absent;
3. calls `ContextCompat.startForegroundService()` only from the visible click path after overlay access exists.

The stop button calls `stopService(Intent(this, OverlayService::class.java))`. Do not add boot or background-start behavior.

- [ ] **Step 5: Implement OverlayService**

On create:

1. create notification channel;
2. call `startForeground()` immediately with a notification reading `Luc 正在陪着你`;
3. validate configuration without logging values;
4. create the overlay controller;
5. create the state coordinator and Handler-backed scheduler;
6. create the Supabase client and polling loop;
7. call `overlayController.show()`;
8. start polling only after show succeeds.

Pet tap callback calls `stateCoordinator.onLocalTap()` immediately, generates one UUID, then calls `supabaseClient.postTap(uuid)`. `onDestroy()` stops polling, closes coordinator, removes overlay, destroys WebViews, and cancels OkHttp calls.

Use the API-specific foreground call:

```kotlin
private fun enterForeground(notification: Notification) {
    val type = if (Build.VERSION.SDK_INT >= 34) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }
    ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        notification,
        type,
    )
}
```

The service skeleton has explicit ownership:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!Settings.canDrawOverlays(this)) {
        stopSelf()
        return START_NOT_STICKY
    }
    ensureRuntimeStarted()
    return START_STICKY
}

override fun onDestroy() {
    pollingLoop?.stop()
    stateCoordinator?.close()
    overlayController?.remove()
    supabaseClient?.cancelAll()
    super.onDestroy()
}
```

- [ ] **Step 6: Verify integration compilation and contracts**

Run:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/luc/body/MainActivity.kt app/src/main/java/com/luc/body/OverlayService.kt app/src/main/res app/src/test/java/com/luc/body/RepositoryContractTest.kt
git commit -m "feat: run Luc as an Android foreground overlay"
```

### Task 8: Signed GitHub Actions Release and Operator Documentation

**Files:**
- Create: `.github/workflows/build.yml`
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `app/src/test/java/com/luc/body/RepositoryContractTest.kt`

**Interfaces:**
- Consumes: all prior tasks and six GitHub Actions secrets.
- Produces: signed `Luc-0.1.0-release.apk` artifact on every push to `main`.

- [ ] **Step 1: Add failing repository release-contract tests**

```kotlin
@Test
fun workflowBuildsAndUploadsSignedRelease() {
    val workflow = locateRepositoryFile(".github/workflows/build.yml").readText()
    assertTrue(workflow.contains("push:"))
    assertTrue(workflow.contains("main"))
    assertTrue(workflow.contains("testDebugUnitTest"))
    assertTrue(workflow.contains("lintRelease"))
    assertTrue(workflow.contains("assembleRelease"))
    assertTrue(workflow.contains("actions/upload-artifact"))
    assertFalse(workflow.contains("sb_publishable_"))
}
```

Add README assertions for all six secret names and Android 16 installation instructions.

- [ ] **Step 2: Run the release contract**

Run: `./gradlew :app:testDebugUnitTest --tests com.luc.body.RepositoryContractTest`

Expected: FAIL because workflow and operator instructions are absent.

- [ ] **Step 3: Add release signing configuration**

`app/build.gradle.kts` reads these environment variables without printing them:

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The `release` build type uses the release signing config only when all four are present. CI checks them before Gradle starts, so a `main` build cannot silently produce an unsigned artifact.

```kotlin
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val keyAliasValue = providers.environmentVariable("ANDROID_KEY_ALIAS")
val keyPasswordValue = providers.environmentVariable("ANDROID_KEY_PASSWORD")

android {
    signingConfigs {
        create("release") {
            if (keystorePath.isPresent) {
                storeFile = file(keystorePath.get())
                storePassword = keystorePassword.orNull
                keyAlias = keyAliasValue.orNull
                keyPassword = keyPasswordValue.orNull
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

- [ ] **Step 4: Create the workflow**

Workflow requirements:

```yaml
name: Build release APK

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
```

Use pinned major actions: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, and `actions/upload-artifact@v4`. Set Java distribution to Temurin 17. Before decoding anything, check each required secret for non-empty presence and emit only the missing variable name.

Decode `ANDROID_KEYSTORE_BASE64` into `${RUNNER_TEMP}/luc-release.jks`, export `ANDROID_KEYSTORE_PATH`, then run:

```bash
yes | "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" "build-tools;36.0.0"
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
```

Copy the generated APK to `Luc-0.1.0-release.apk`, run `keytool -printcert -jarfile` to prove it is signed without printing passwords, and upload the renamed file with 30-day retention.

- [ ] **Step 5: Document setup and operation**

README must include:

- application purpose and MVP scope;
- Android 16 install and overlay-permission steps;
- local placeholder configuration via ignored `local.properties`;
- exact GitHub secret names;
- how to generate a persistent personal keystore with `keytool`;
- how to base64-encode it without printing passwords;
- where to download the Actions artifact;
- known limitations: Doze/OEM termination and temporary `allow_all` RLS.

Use placeholders such as `YOUR_SUPABASE_URL` and never real values.

- [ ] **Step 6: Run the complete pre-push verification**

Run:

```bash
./gradlew --no-daemon clean testDebugUnitTest lintRelease assembleDebug
```

Expected: PASS.

If a release keystore is available locally through environment variables, also run:

```bash
./gradlew --no-daemon assembleRelease
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

Expected: release build PASS and certificate details present.

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/build.yml app/build.gradle.kts README.md app/src/test/java/com/luc/body/RepositoryContractTest.kt
git commit -m "ci: build signed Luc release APK"
```

## Integrated Acceptance Gate

- [ ] All JVM tests pass.
- [ ] Android Lint release task passes.
- [ ] Debug APK builds locally or in CI.
- [ ] GitHub Actions produces a signed `Luc-0.1.0-release.apk`.
- [ ] APK package is `com.luc.body`, label is `Luc`, target SDK is 36, and version is `0.1.0`.
- [ ] Android 16 manual test confirms permission denial does not crash.
- [ ] Android 16 manual test confirms the two overlays appear above other apps.
- [ ] Bubble and transparent area allow underlying-app touch.
- [ ] Only the `120x120dp` pet rectangle accepts tap and drag.
- [ ] Dragging uses raw coordinates without jumping and keeps the two windows synchronized.
- [ ] Tap reaction appears immediately for 1.2 seconds and reports one backend event.
- [ ] Remote state appears within one completed poll interval plus network latency.
- [ ] Repeated `updated_at` does not replay the bubble.
- [ ] Offline or HTTP failures preserve the last valid display.
- [ ] Closing MainActivity leaves the foreground service and pet running.
- [ ] Repository and workflow logs contain no real credentials or signing material.

## Spec Coverage Map

| Approved design requirement | Implemented by |
|---|---|
| Android 16 project identity and SDK | Task 1 |
| Local/remote state precedence and bubble dedupe | Task 2 |
| Raw-coordinate tap/drag rules and geometry | Task 3 |
| Four SVG states, CSS animation, safe WebView | Task 4 |
| Dual-header Supabase polling and tap POST | Task 5 |
| Two synchronized touch/pass-through windows | Task 6 |
| Permission guidance and foreground service | Task 7 |
| Signed release APK and operator documentation | Task 8 |

