package com.miowser.app.browser

import org.junit.Assert.*
import org.junit.Test

class WebPermissionPolicyTest {
    @Test fun namesEachCapabilityIncludingUnknownIdentifiers() {
        assertEquals("Camera", WebPermissionPolicy.label(WebPermissionPolicy.CAMERA))
        assertEquals("Microphone", WebPermissionPolicy.label(WebPermissionPolicy.MICROPHONE))
        assertTrue(WebPermissionPolicy.label(WebPermissionPolicy.PROTECTED_MEDIA).contains("DRM"))
        assertTrue(WebPermissionPolicy.label(WebPermissionPolicy.MIDI).contains("MIDI"))
        assertTrue(WebPermissionPolicy.label("vendor.future.CAPABILITY").contains("vendor.future.CAPABILITY"))
    }
    @Test fun protectedPlaybackHasExplicitConfirmationWithoutCameraOrMicrophonePermission() {
        val review = WebPermissionPolicy.review(listOf(WebPermissionPolicy.PROTECTED_MEDIA))
        assertTrue(review.valid && review.sensitive && review.canRemember)
        assertTrue(review.runtimePermissions.isEmpty())
        assertTrue(review.confirmation!!.contains("license"))
        assertTrue(review.explanation.contains("does not provide camera or microphone"))
    }
    @Test fun midiAndUnknownResourcesCanBeReviewedInsteadOfHardBlocked() {
        val midi = WebPermissionPolicy.review(listOf(WebPermissionPolicy.MIDI))
        assertTrue(midi.valid && midi.sensitive && midi.canRemember)
        assertTrue(midi.explanation.contains("firmware"))
        val future = WebPermissionPolicy.review(listOf("vendor.future.CAPABILITY"))
        assertTrue(future.valid && future.sensitive)
        assertFalse(future.canRemember)
        assertNotNull(future.confirmation)
        assertTrue(future.explanation.contains("allow this request once"))
    }
    @Test fun androidPermissionsAreRequestedOnlyForReviewedCaptureCapabilities() {
        val review = WebPermissionPolicy.review(listOf(WebPermissionPolicy.CAMERA, WebPermissionPolicy.MICROPHONE,
            WebPermissionPolicy.CAMERA, WebPermissionPolicy.PROTECTED_MEDIA))
        assertEquals(3, review.resources.size)
        assertEquals(listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"), review.runtimePermissions)
        assertFalse(WebPermissionPolicy.review(listOf(WebPermissionPolicy.CAMERA)).sensitive)
    }
    @Test fun malformedRequestsAndInsecureOriginsRemainExplicitlyUnavailable() {
        assertFalse(WebPermissionPolicy.review(emptyList()).valid)
        assertFalse(WebPermissionPolicy.review(listOf("")).valid)
        assertTrue(WebPermissionPolicy.secureOrigin("https://aryplus.tv/"))
        assertFalse(WebPermissionPolicy.secureOrigin("http://aryplus.tv/"))
        assertFalse(WebPermissionPolicy.secureOrigin("https://user@example.com"))
        assertFalse(WebPermissionPolicy.secureOrigin("https:///missing-host"))
    }
    @Test fun grantsOnlyReviewedRequestedSubsetAndReleasesOnce() {
        var granted = emptyList<String>()
        var denied = 0
        var completed = 0
        val requested = mutableSetOf(WebPermissionPolicy.CAMERA, WebPermissionPolicy.MICROPHONE)
        val resolution = WebPermissionResolution(requested, { granted = it }, { denied++ }, { completed++ })
        requested.add("foreign")
        resolution.grant(listOf(WebPermissionPolicy.MICROPHONE, "foreign", WebPermissionPolicy.MICROPHONE))
        resolution.deny()
        resolution.cancel()
        assertEquals(listOf(WebPermissionPolicy.MICROPHONE), granted)
        assertEquals(0, denied)
        assertEquals(1, completed)
        assertFalse(resolution.pending)
    }
    @Test fun cancellationCannotLaterGrantOrDenyTheOldPlatformRequest() {
        var platformCalls = 0
        var completed = 0
        val resolution = WebPermissionResolution(setOf(WebPermissionPolicy.PROTECTED_MEDIA),
            { platformCalls++ }, { platformCalls++ }, { completed++ })
        resolution.cancel()
        resolution.grant(listOf(WebPermissionPolicy.PROTECTED_MEDIA))
        resolution.deny()
        assertEquals(0, platformCalls)
        assertEquals(1, completed)
    }
    @Test fun unrelatedGrantCannotEscalateTheRequest() {
        var denied = 0
        val resolution = WebPermissionResolution(setOf(WebPermissionPolicy.CAMERA),
            { fail("Unexpected grant") }, { denied++ }, {})
        resolution.grant(listOf(WebPermissionPolicy.MICROPHONE))
        assertEquals(1, denied)
    }
    @Test fun resolvedRequestsAreReleasedEvenWhenPlatformCallFails() {
        var completed = 0
        val resolution = WebPermissionResolution(setOf(WebPermissionPolicy.CAMERA),
            { error("Page already gone") }, {}, { completed++ })
        runCatching { resolution.grant(listOf(WebPermissionPolicy.CAMERA)) }
        assertFalse(resolution.pending)
        assertEquals(1, completed)
    }
}
