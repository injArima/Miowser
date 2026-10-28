package com.miowser.app.diagnostics

import android.util.Log
import com.miowser.app.BuildConfig

internal object SafeLog {
    fun warning(tag: String, message: String, error: Throwable? = null) {
        CrashDiagnostics.recordLog(tag, "warning", error?.javaClass?.simpleName)
        Log.w(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
        if (error != null) debug(tag, error.message.orEmpty())
    }
    fun error(tag: String, message: String, error: Throwable? = null) {
        CrashDiagnostics.recordLog(tag, "error", error?.javaClass?.simpleName)
        Log.e(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
        if (error != null) debug(tag, error.message.orEmpty())
    }
    fun info(tag: String, message: String) {
        CrashDiagnostics.recordLog(tag, "info", null)
        Log.i(tag, redactDiagnostic(message))
    }
    fun debug(tag: String, message: String, error: Throwable? = null) {
        CrashDiagnostics.recordLog(tag, "debug", error?.javaClass?.simpleName)
        if (BuildConfig.DEBUG) Log.d(tag, redactDiagnostic(message) + (error?.let { " (${it.javaClass.simpleName})" } ?: ""))
    }
}
