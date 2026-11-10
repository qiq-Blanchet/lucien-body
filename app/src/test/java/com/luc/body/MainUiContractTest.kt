package com.luc.body

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiContractTest {
    @Test
    fun `moonlit layout keeps functional controls and removes ineffective sliders`() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        listOf(
            "boot_switch",
            "self_talk_switch",
            "self_talk_frequency_seek",
            "loneliness_switch",
            "app_awareness_switch",
            "supabase_url_input",
            "supabase_key_input",
            "app_details_button",
            "overlay_permission_button",
            "usage_permission_button",
            "reset_position_button",
            "save_settings_button",
            "start_button",
            "stop_button",
        ).forEach { id -> assertTrue("missing $id", layout.contains("@+id/$id")) }

        assertTrue(layout.contains("android:min=\"30\""))
        assertTrue(layout.contains("android:max=\"60\""))
        assertFalse(layout.contains("pet_size_seek"))
        assertFalse(layout.contains("pet_size_label"))
        assertFalse(layout.contains("bubble_duration_seek"))
        assertFalse(layout.contains("bubble_duration_label"))
        assertTrue(layout.contains("@+id/settings_overlay"))
    }

    @Test
    fun `all visible action buttons remain wired to their original behavior`() {
        val source = File("src/main/java/com/luc/body/MainActivity.kt").readText()

        assertTrue(source.contains("R.id.start_button).setOnClickListener { startFromVisibleClick() }"))
        assertTrue(source.contains("R.id.stop_button).setOnClickListener"))
        assertTrue(source.contains("R.id.save_settings_button).setOnClickListener { saveSettings() }"))
        assertTrue(source.contains("R.id.reset_position_button).setOnClickListener"))
        assertTrue(source.contains("R.id.overlay_permission_button).setOnClickListener { openOverlaySettings() }"))
        assertTrue(source.contains("R.id.usage_permission_button).setOnClickListener { openUsageSettings() }"))
        assertTrue(source.contains("R.id.settings_app_details_button).apply"))
        assertTrue(source.contains("R.id.permission_help_button).setOnClickListener"))
        assertTrue(source.contains("R.id.settings_button).setOnClickListener"))
        assertTrue(source.contains("R.id.settings_close_button).setOnClickListener"))
    }

    @Test
    fun `long press menu is a compact vertical three action panel`() {
        val source = File("src/main/java/com/luc/body/overlay/OverlayController.kt").readText()

        assertTrue(source.contains("\"戳一下\" to actions.onPoke"))
        assertTrue(source.contains("\"摸摸头\" to actions.onPetHead"))
        assertTrue(source.contains("\"隐藏\" to actions.onHide"))
        assertFalse(source.contains("\"设置\" to actions.onSettings"))
        assertTrue(source.contains(").forEach { (label, action) ->"))
        assertTrue(!source.contains("chunked(2)"))
        assertTrue(source.contains("windowManager.addView(content, menuParams)"))
        assertTrue(!source.contains("PopupWindow("))
        assertTrue(source.contains("Color.rgb(255, 247, 250)"))
        assertTrue(source.contains("Color.rgb(255, 237, 243)"))
    }

    @Test
    fun `app entry has no automatic permission dialogs and runtime settings are real`() {
        val activity = File("src/main/java/com/luc/body/MainActivity.kt").readText()
        val service = File("src/main/java/com/luc/body/OverlayService.kt").readText()

        assertFalse(activity.contains("AlertDialog"))
        assertFalse(activity.contains("showNextPermissionGuide"))
        assertFalse(activity.contains("pet_size_seek"))
        assertFalse(activity.contains("bubble_duration_seek"))
        assertTrue(service.contains("FIXED_PET_SIZE_DP = 90"))
        assertTrue(service.contains("FIXED_BUBBLE_DURATION_SECONDS = 10"))
    }

    @Test
    fun `fixed visual design ignores oem font replacement and hides scroll indicators`() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        val theme = File("src/main/res/values/themes.xml").readText()

        assertTrue(layout.contains("android:scrollbars=\"none\""))
        assertTrue(theme.contains("<item name=\"android:fontFamily\">sans-serif</item>"))
    }

    @Test
    fun `action icons use fixed image views instead of font baseline compound drawables`() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()

        assertFalse(layout.contains("android:drawableTop=\"@drawable/ic_luc_"))
        listOf(
            "settings_button",
            "settings_app_details_button",
            "overlay_permission_button",
            "usage_permission_button",
            "reset_position_button",
            "save_settings_button",
            "start_button",
            "stop_button",
        ).forEach { id ->
            assertTrue(
                "$id must use a stable layout container",
                Regex("<LinearLayout[\\s\\S]{0,240}android:id=\\\"@\\+id/$id\\\"").containsMatchIn(layout),
            )
        }
    }

    @Test
    fun `settings gear and splash mascot use symmetric fixed vector geometry`() {
        val gear = File("src/main/res/drawable/ic_luc_settings.xml").readText()
        val mascot = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val splashTheme = File("src/main/res/values-v31/themes.xml").readText()

        assertTrue(gear.contains("M10.5,1h3v4h-3z"))
        assertTrue(gear.contains("M10.5,19h3v4h-3z"))
        assertTrue(gear.contains("M1,10.5h4v3h-4z"))
        assertTrue(gear.contains("M19,10.5h4v3h-4z"))
        assertTrue(mascot.contains("Exact still-frame geometry from assets/clawd_sprites/idle_1.svg"))
        assertTrue(mascot.contains("#DE886D"))
        assertTrue(splashTheme.contains("android:windowSplashScreenAnimatedIcon"))
    }

    @Test
    fun `reset position uses a compact complete restart icon`() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        val resetIcon = File("src/main/res/drawable/ic_luc_reset.xml").readText()
        val resetButton = Regex(
            "android:id=\\\"@\\+id/reset_position_button\\\"[\\s\\S]{0,700}?@drawable/ic_luc_reset",
        ).find(layout)?.value.orEmpty()

        assertTrue(resetButton.contains("android:layout_width=\"16dp\""))
        assertTrue(resetButton.contains("android:layout_height=\"16dp\""))
        assertFalse(layout.contains("android:translationY=\"-1dp\""))
        assertTrue(resetIcon.contains("M12,5V2L7,7l5,5V8"))
        assertFalse(resetIcon.contains("M20,11a8,8"))
    }

    @Test
    fun `launcher uses a dedicated colored icon rather than the monochrome notification asset`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue(!manifest.contains("android:icon=\"@drawable/ic_notification\""))
    }
}
