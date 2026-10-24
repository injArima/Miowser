package com.miowser.app.diagnostics

private val diagnosticUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
internal fun safeDiagnosticUrl(value: String): String = runCatching {
    val uri = java.net.URI(value)
    if (uri.scheme !in listOf("http", "https") || uri.host == null) "[url]"
    else java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString().take(256)
}.getOrDefault("[url]")
internal fun redactDiagnostic(value: String): String = diagnosticUrl.replace(value) { safeDiagnosticUrl(it.value) }
