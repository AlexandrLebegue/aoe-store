package dev.proxboss.aoestore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlMapperTest {

    @Test
    fun `normalise l URL du serveur`() {
        assertEquals("http://192.168.1.63:8099", UrlMapper.normalizeBase("192.168.1.63:8099/"))
        assertEquals("https://store.example", UrlMapper.normalizeBase("https://store.example///"))
        assertEquals(UrlMapper.DEFAULT_SERVER, UrlMapper.normalizeBase("  "))
    }

    @Test
    fun `mappe les chemins relatifs et absolus`() {
        assertEquals(
            "http://host:8099/files/icon.png",
            UrlMapper.resolve("http://host:8099/", "/files/icon.png"),
        )
        assertEquals(
            "http://host:8099/files/icon.png",
            UrlMapper.resolve("http://host:8099", "files/icon.png"),
        )
        assertEquals(
            "https://cdn.example/icon.png",
            UrlMapper.resolve("http://host:8099", "https://cdn.example/icon.png"),
        )
        assertNull(UrlMapper.resolve("http://host:8099", null))
        assertNull(UrlMapper.resolve("http://host:8099", "   "))
    }

    @Test
    fun `construit les URLs de l API`() {
        assertEquals("http://host:8099/api/health", UrlMapper.health("http://host:8099/"))
        assertEquals("http://host:8099/api/apps", UrlMapper.catalog("http://host:8099"))
        assertEquals(
            "http://host:8099/api/apps/aoe-companion",
            UrlMapper.app("http://host:8099", "aoe-companion"),
        )
        assertEquals(
            "http://host:8099/api/apps/aoe-companion/versions/0.9.2/download",
            UrlMapper.download("http://host:8099", "aoe-companion", "0.9.2"),
        )
    }
}
