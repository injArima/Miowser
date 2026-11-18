package com.miowser.app.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class ProtectedPageLoadTest {
    @Test fun aCachedEngineStillWaitsForSavedSitePolicy() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val savedPolicy = CompletableDeferred<Unit>()
            var policyReady = false
            var navigated = false
            val gate = ProtectedPageLoad(scope, { policyReady }, { savedPolicy.await(); policyReady = true })
            gate.execute({ true }, { throw it }) { navigated = true }
            assertFalse(navigated)
            savedPolicy.complete(Unit)
            assertTrue(navigated)
        } finally { scope.cancel() }
    }

    @Test fun cachedReadinessKeepsNormalNavigationSynchronous() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var prepared = 0
            var navigated = 0
            ProtectedPageLoad(scope, { true }, { prepared++ })
                .execute({ true }, { throw it }) { navigated++ }
            assertEquals(1, navigated)
            assertEquals(0, prepared)
        } finally { scope.cancel() }
    }

    @Test fun firstNavigationWaitsAndANewerUrlSupersedesTheOldOne() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sharedProtection = CompletableDeferred<Unit>()
            val navigations = mutableListOf<String>()
            val gate = ProtectedPageLoad(scope, { false }, { sharedProtection.await() })
            gate.execute({ true }, { throw it }) { navigations += "old" }
            assertTrue(navigations.isEmpty())
            assertTrue(gate.waiting)
            gate.execute({ true }, { throw it }) { navigations += "new" }
            assertFalse(sharedProtection.isCancelled)
            sharedProtection.complete(Unit)
            assertEquals(listOf("new"), navigations)
            assertFalse(gate.waiting)
        } finally { scope.cancel() }
    }

    @Test fun closingATabOrRetiringItsEngineCannotLoadAStalePage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val readiness = CompletableDeferred<Unit>()
            var alive = true
            var navigations = 0
            val gate = ProtectedPageLoad(scope, { false }, { readiness.await() })
            gate.execute({ alive }, { throw it }) { navigations++ }
            alive = false
            readiness.complete(Unit)
            assertEquals(0, navigations)
            val next = CompletableDeferred<Unit>()
            val cancelled = ProtectedPageLoad(scope, { false }, { next.await() })
            cancelled.execute({ true }, { throw it }) { navigations++ }
            cancelled.cancel()
            next.complete(Unit)
            assertEquals(0, navigations)
        } finally { scope.cancel() }
    }

    @Test fun preparationFailureNeverFallsThroughToAnUnprotectedPage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val failure = IllegalStateException("Saved protection unavailable")
            var reported: Exception? = null
            var navigated = false
            ProtectedPageLoad(scope, { false }, { throw failure })
                .execute({ true }, { reported = it }) { navigated = true }
            assertSame(failure, reported)
            assertFalse(navigated)
        } finally { scope.cancel() }
    }
}
