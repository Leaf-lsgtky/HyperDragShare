package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ModernOverlayOwnerTest {
    @Test
    fun overlayRootPropagatesComposeViewTreeOwners() {
        val payload = CapturedContent.text("现代样式", "pkg", Rect())!!
        val overlay = ModernPreviewOverlayView(
            RuntimeEnvironment.getApplication(),
            payload,
            DragShareSettings.defaults(),
        )
        val composeView = overlay.getChildAt(0)

        assertNotNull(overlay.findViewTreeLifecycleOwner())
        assertNotNull(overlay.findViewTreeSavedStateRegistryOwner())
        assertNotNull(overlay.findViewTreeViewModelStoreOwner())
        assertNotNull(composeView.findViewTreeLifecycleOwner())
        assertNotNull(composeView.findViewTreeSavedStateRegistryOwner())
        assertNotNull(composeView.findViewTreeViewModelStoreOwner())
        assertTrue(overlay.clipToOutline)
    }
}
