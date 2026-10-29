package com.miowser.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Android's system-wide text-selection entry point. The source app supplies selected text through
 * ACTION_PROCESS_TEXT; this no-display activity forwards it to Miowser's singleTask activity and
 * immediately leaves the source app's task.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = TextSearchActions.normalizedQuery(
            intent.takeIf { it.action == Intent.ACTION_PROCESS_TEXT }
                ?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        )

        if (selected != null) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = TextSearchActions.SEARCH_SELECTED_TEXT
                putExtra(Intent.EXTRA_PROCESS_TEXT, selected)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        finish()
    }
}

