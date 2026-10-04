package dev.re7gog.b_sideloader.domain.selection

import dev.re7gog.b_sideloader.testing.ARM32_ABIS
import dev.re7gog.b_sideloader.testing.ARM64_ABIS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbiMatcherTest {

    @Test
    fun `universal builds run anywhere`() {
        assertTrue(AbiMatcher.runsOn("app-universal-release.apk", ARM64_ABIS))
        assertTrue(AbiMatcher.runsOn("app-release.apk", ARM32_ABIS))
    }

    @Test
    fun `a matching abi marker runs`() {
        assertTrue(AbiMatcher.runsOn("app-arm64-v8a-release.apk", ARM64_ABIS))
    }

    @Test
    fun `a foreign abi marker does not run`() {
        assertFalse(AbiMatcher.runsOn("app-x86_64-release.apk", ARM64_ABIS))
        assertFalse(AbiMatcher.runsOn("app-arm64-v8a-release.apk", ARM32_ABIS))
    }

    /**
     * A 64-bit device also runs the 32-bit split, so the *whole* `SUPPORTED_ABIS` list has to be
     * consulted rather than only the primary ABI.
     */
    @Test
    fun `64 bit device accepts the 32 bit split`() {
        assertTrue(AbiMatcher.runsOn("app-armeabi-v7a-release.apk", ARM64_ABIS))
    }

    @Test
    fun `abi detection is case insensitive`() {
        assertTrue(AbiMatcher.runsOn("App-ARM64-V8A-release.APK", ARM64_ABIS))
    }
}
