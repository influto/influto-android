package to.influ.sdk.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SentPurchaseStoreTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun marks_and_persists_across_instances() {
        val a = SentPurchaseStore(ctx)
        assertFalse(a.isSent("tok_abc"))
        a.markSent("tok_abc")
        assertTrue(a.isSent("tok_abc"))
        // a fresh instance over the same prefs file sees it (cross-launch persistence)
        assertTrue(SentPurchaseStore(ctx).isSent("tok_abc"))
        // unrelated token is independent
        assertFalse(SentPurchaseStore(ctx).isSent("tok_other"))
    }
}
