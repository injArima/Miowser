package com.miowser.app.diagnostics

import org.junit.Assert.*
import org.junit.Test

class SafeDiagnosticTest {
    @Test fun queriesFragmentsAndUserCredentialsNeverReachDiagnostics() {
        val redacted = redactDiagnostic("Failed https://user:password@example.org/movie.mp4?token=secret&sig=private#identity")
        assertEquals("Failed https://example.org/movie.mp4", redacted)
    }
    @Test fun malformedUrlsAndUntrustedSchemesAreHidden() {
        assertEquals("[url]", safeDiagnosticUrl("not a url"))
        assertEquals("[url]", safeDiagnosticUrl("javascript:private"))
        assertEquals("https://example.org:8443/a", safeDiagnosticUrl("https://example.org:8443/a?q=private"))
    }
}
