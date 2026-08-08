package com.luc.body

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun bootStartsOnlyWhenEnabledAndOverlayPermissionExists() {
        assertTrue(BootReceiver.shouldStartOnBoot(Intent.ACTION_BOOT_COMPLETED, true, true))
        assertFalse(BootReceiver.shouldStartOnBoot(Intent.ACTION_BOOT_COMPLETED, false, true))
        assertFalse(BootReceiver.shouldStartOnBoot(Intent.ACTION_BOOT_COMPLETED, true, false))
        assertFalse(BootReceiver.shouldStartOnBoot("other", true, true))
        assertFalse(BootReceiver.shouldStartOnBoot(null, true, true))
    }
}
