package com.miowser.app.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side regression for a chooser result arriving after its WebView tab is gone. */
@RunWith(AndroidJUnit4::class)
class BrowserFileSelectionTest {
    @Test fun retiredTabCannotReceiveLatePickerResult() {
        val selection = BrowserFileSelection()
        var firstCalls = 0
        var firstResult: Array<Uri>? = null
        val first = ValueCallback<Array<Uri>> { firstCalls++; firstResult = it }
        assertTrue(selection.start(1L, first))

        var secondCalls = 0
        assertFalse(selection.start(2L, ValueCallback { secondCalls++ }))
        assertEquals(1, secondCalls)
        selection.cancelFor(2L)
        assertEquals(1L, selection.ownerId)

        selection.cancelFor(1L)
        assertEquals(1, firstCalls)
        assertNull(firstResult)
        assertNull(selection.ownerId)
        var prematureCalls = 0
        assertFalse(selection.start(3L, ValueCallback { prematureCalls++ }))
        assertEquals(1, prematureCalls)
        selection.complete(Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://documents.provider/late")), "com.miowser.app")
        assertEquals(1, firstCalls)

        var resumed: Array<Uri>? = null
        assertTrue(selection.start(3L, ValueCallback { resumed = it }))
        selection.complete(Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://documents.provider/new")), "com.miowser.app")
        assertEquals("content://documents.provider/new", resumed?.single()?.toString())
        assertNull(selection.ownerId)
    }

    @Test fun chooserRejectsNonContentAndOwnProviderUris() {
        val selection = BrowserFileSelection()
        var received: Array<Uri>? = null
        assertTrue(selection.start(1L, ValueCallback { received = it }))
        selection.complete(Activity.RESULT_OK,
            Intent().setData(Uri.parse("file:///sdcard/private.txt")), "com.miowser.app")
        assertNull(received)

        assertTrue(selection.start(1L, ValueCallback { received = it }))
        selection.complete(Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.miowser.app.fileprovider/private")), "com.miowser.app")
        assertNull(received)
    }
}
