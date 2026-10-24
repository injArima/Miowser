package com.miowser.app.diagnostics

import android.util.Log
import com.miowser.app.BuildConfig

internal object SafeLog {
    fun warning(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
        if (error != null) debug(tag, error.message.orEmpty())
    }
    fun error(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
        if (error != null) debug(tag, error.message.orEmpty())
    }
    fun info(tag: String, message: String) { Log.i(tag, redactDiagnostic(message)) }
    fun debug(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
    }
}
