package com.codexapp.update

import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun compareVersionsDetectsNewerSemanticVersion() {
        assertTrue(compareVersions("0.1.9", "0.1.8") > 0)
        assertTrue(compareVersions("v0.2.0", "0.1.9") > 0)
    }

    @Test
    fun compareVersionsTreatsEqualPrefixAsNotNewer() {
        assertTrue(compareVersions("0.1.8", "0.1.8") == 0)
        assertTrue(compareVersions("0.1.8", "0.1.9") < 0)
    }

    @Test
    fun startupGateAllowsOnlyOneCheckPerProcess() {
        AppUpdateStartupGate.resetForTest()

        val first = AppUpdateStartupGate.consume()
        val second = AppUpdateStartupGate.consume()

        assertTrue(first)
        assertTrue(!second)
    }
}
