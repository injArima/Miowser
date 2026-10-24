package com.miowser.app.adblock

/** A blocked top-level navigation must not masquerade as a successfully loaded empty page. */
private fun blockedPageText(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

internal fun blockedDocumentHtml(rule: String, sources: List<FilterRuleSource> = emptyList()): String {
    val safeRule = blockedPageText(rule.take(2_048))
    val sourceRows = sources.joinToString("") { source ->
        "<li><strong>${blockedPageText(source.name.take(512))}</strong>" +
            (source.url?.let { "<br>${blockedPageText(it.take(8_192))}" } ?: "") + "</li>"
    }.ifEmpty { "<li>Rule source unavailable</li>" }
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Page blocked</title><style>
        :root{color-scheme:light dark}body{font:16px system-ui;margin:0;padding:32px;line-height:1.5}
        main{max-width:40rem;margin:10vh auto}pre{white-space:pre-wrap;overflow-wrap:anywhere}
        </style></head><body><main><h1>Page blocked by ad blocker</h1>
        <p>Open the shield or site settings to review protection for this website, then reload.</p>
        <details><summary>Blocking lists (${sources.size})</summary><ul>$sourceRows</ul></details>
        <details><summary>Matching rule</summary><pre>$safeRule</pre></details></main></body></html>
    """.trimIndent()
}
