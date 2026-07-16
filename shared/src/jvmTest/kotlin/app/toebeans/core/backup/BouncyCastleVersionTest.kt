package app.toebeans.core.backup

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Failing-test commit per AGENTS.md § Test-as-spec rules.
 * Asserts the resolved BouncyCastle version is >= 1.80.2.
 * This test MUST fail before the version catalog is bumped,
 * and MUST pass after.
 */
class BouncyCastleVersionTest {
    @Test
    fun `resolved BouncyCastle version is at least 1_80_2`() {
        val provider =
            org
                .bouncycastle
                .jce
                .provider
                .BouncyCastleProvider()
        val info = provider.info
        // BC versions can be two-part (e.g. v1.84) or three-part (e.g. v1.80.2).
        val versionRegex = """v(\d+)\.(\d+)(?:\.(\d+))?""".toRegex()
        val match =
            versionRegex
                .find(info)
                ?: error("Could not parse BouncyCastle version from: $info")
        val (majorStr, minorStr, patchStr) = match.destructured
        val major = majorStr.toInt()
        val minor = minorStr.toInt()
        val patch = patchStr.toIntOrNull() ?: 0

        val actual = major * 10_000 + minor * 100 + patch
        val expected = 1 * 10_000 + 80 * 100 + 2 // 1.80.2

        assertTrue(
            actual >= expected,
            "BouncyCastle version must be >= 1.80.2 (was $major.$minor.$patch). " +
                "Bump gradle/libs.versions.toml bouncycastle version.",
        )
    }
}
