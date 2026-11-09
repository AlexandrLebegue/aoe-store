package dev.proxboss.aoestore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {

    private val catalogJson = """
        [
          {
            "slug": "aoe-companion",
            "name": "AoE Companion",
            "tagline": "Piloter Agent of Empires depuis le téléphone",
            "developer": "ProxBoss",
            "category": "Outils",
            "iconUrl": "/files/apps/aoe-companion/versions/v11/icon.png",
            "coverUrls": ["/files/1.png", "/files/2.png", "/files/3.png"],
            "accentColor": "#3B82F6",
            "updatedAt": "2026-11-09T00:28:35.101Z",
            "downloads": 12,
            "latestVersion": {
              "version": "0.9.2",
              "versionCode": 11,
              "sizeBytes": 61820365,
              "publishedAt": "2026-11-09T00:28:35.101Z"
            }
          }
        ]
    """.trimIndent()

    @Test
    fun `parse un catalogue complet`() {
        val apps = CatalogParser.parseCatalog(catalogJson)

        assertEquals(1, apps.size)
        val app = apps.first()
        assertEquals("aoe-companion", app.slug)
        assertEquals("AoE Companion", app.name)
        assertEquals("ProxBoss", app.developer)
        assertEquals("Outils", app.category)
        assertEquals("#3B82F6", app.accentColor)
        assertEquals(3, app.coverUrls.size)
        assertEquals(12L, app.downloads)
        assertEquals("0.9.2", app.latestVersion?.version)
        assertEquals(11, app.latestVersion?.versionCode)
        assertEquals(61820365L, app.latestVersion?.sizeBytes)
    }

    @Test
    fun `accepte une enveloppe apps`() {
        val apps = CatalogParser.parseCatalog("""{"apps":$catalogJson}""")
        assertEquals(1, apps.size)
        assertEquals("aoe-companion", apps.first().slug)
    }

    @Test
    fun `accepte latestVersion sous forme de chaine`() {
        val apps = CatalogParser.parseCatalog(
            """[{"slug":"x","name":"X","latestVersion":"1.2.3"}]""",
        )
        assertEquals("1.2.3", apps.first().latestVersion?.version)
        assertEquals(0, apps.first().latestVersion?.versionCode)
    }

    @Test
    fun `tolere les champs optionnels absents`() {
        val apps = CatalogParser.parseCatalog("""[{"slug":"minimal","name":"Minimal"}]""")
        val app = apps.first()
        assertNull(app.iconUrl)
        assertTrue(app.coverUrls.isEmpty())
        assertEquals("Applications", app.category)
        assertEquals("#5B8CFF", app.accentColor)
        assertNull(app.latestVersion)
    }

    @Test
    fun `parse le detail et ses versions`() {
        val detailJson = """
            {
              "slug": "aoe-companion",
              "name": "AoE Companion",
              "tagline": "Accroche",
              "developer": "ProxBoss",
              "category": "Outils",
              "iconUrl": "/files/icon.png",
              "coverUrls": ["/files/1.png"],
              "accentColor": "#3B82F6",
              "updatedAt": "2026-11-09T00:00:00.000Z",
              "downloads": 4,
              "latestVersion": {"version": "0.9.3", "versionCode": 12},
              "description": "Description longue",
              "package": "dev.proxboss.aoe",
              "createdAt": "2026-11-01T00:00:00.000Z",
              "versions": [
                {
                  "version": "0.9.2",
                  "versionCode": 11,
                  "changelog": "Corrections",
                  "sizeBytes": 1000,
                  "sha256": "abc",
                  "publishedAt": "2026-11-02T00:00:00.000Z",
                  "downloadUrl": "/api/apps/aoe-companion/versions/0.9.2/download"
                },
                {
                  "version": "0.9.3",
                  "versionCode": 12,
                  "changelog": "Nouveautés",
                  "sizeBytes": 2000,
                  "sha256": "def",
                  "publishedAt": "2026-11-03T00:00:00.000Z",
                  "downloadUrl": "/api/apps/aoe-companion/versions/0.9.3/download"
                }
              ]
            }
        """.trimIndent()

        val detail = CatalogParser.parseDetail(detailJson)

        assertEquals("Description longue", detail.description)
        assertEquals("dev.proxboss.aoe", detail.packageName)
        assertEquals(2, detail.versions.size)
        assertEquals("0.9.2", detail.versions[0].version)
        assertEquals("def", detail.versions[1].sha256)
        assertEquals(2000L, detail.versions[1].sizeBytes)
    }
}
