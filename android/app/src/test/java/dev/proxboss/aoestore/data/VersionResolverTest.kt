package dev.proxboss.aoestore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionResolverTest {

    private fun version(name: String, code: Int) = VersionInfo(
        version = name,
        versionCode = code,
        changelog = "",
        sizeBytes = 0L,
        sha256 = "",
        publishedAt = "",
        downloadUrl = "",
    )

    @Test
    fun `compare les versions semantiques et pas seulement alphabetiquement`() {
        assertTrue(VersionResolver.compareVersionNames("1.10.0", "1.9.9") > 0)
        assertTrue(VersionResolver.compareVersionNames("2.0.0", "2.0.0-rc.1") > 0)
        assertTrue(VersionResolver.compareVersionNames("1.0.0", "1.0.1") < 0)
        assertEquals(0, VersionResolver.compareVersionNames("v1.0.0", "1.0.0"))
    }

    @Test
    fun `trie par versionCode decroissant`() {
        val versions = listOf(
            version("1.0.0", 1),
            version("1.2.0", 3),
            version("1.1.0", 2),
        )
        assertEquals(
            listOf("1.2.0", "1.1.0", "1.0.0"),
            VersionResolver.sortDescending(versions).map { it.version },
        )
        assertEquals("1.2.0", VersionResolver.latest(versions)?.version)
    }

    @Test
    fun `detecte les mises a jour`() {
        val latest = version("1.4.0", 14)
        assertTrue(VersionResolver.updateAvailable(13, latest))
        assertFalse(VersionResolver.updateAvailable(14, latest))
        assertFalse(VersionResolver.updateAvailable(null, latest))
        assertTrue(VersionResolver.isUpToDate(15, latest))
    }

    @Test
    fun `retrouve une version par nom ou par code`() {
        val versions = listOf(version("0.9.2", 11), version("0.9.3", 12))
        assertNotNull(VersionResolver.findVersion(versions, "0.9.2"))
        assertEquals(12, VersionResolver.findVersion(versions, "12")?.versionCode)
        assertEquals(null, VersionResolver.findVersion(versions, "9.9.9"))
    }
}
