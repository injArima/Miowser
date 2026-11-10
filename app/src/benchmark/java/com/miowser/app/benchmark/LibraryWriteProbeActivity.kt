package com.miowser.app.benchmark

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.MediaLibrary
import com.miowser.app.persistence.Persistence
import kotlinx.coroutines.launch

/** Drives the real repository using only the benchmark package's synthetic Library. */
class LibraryWriteProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val label = TextView(this).apply { text = "Loading Library fixture" }
        setContentView(label)
        lifecycleScope.launch {
            MediaLibrary.restoreAsync(this@LibraryWriteProbeActivity)
            prepareWrite(label)
        }
    }

    private fun prepareWrite(label: TextView) {
        val entry = MediaLibrary.entries.first { it.folderId == null && it.sourceKind == LibrarySourceKind.LOCAL_FILE }
        label.text = "Library write ready"
        label.setOnClickListener {
            label.isEnabled = false
            check(entry.positionMs == 0L)
            MediaLibrary.updateProgress(this, entry.identityKey, 12_345, entry.durationMs)
            Persistence.afterWrites { label.text = "Library write committed" }
        }
    }
}
