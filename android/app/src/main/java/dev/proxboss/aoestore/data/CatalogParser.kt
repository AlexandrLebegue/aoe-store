package dev.proxboss.aoestore.data

import org.json.JSONArray
import org.json.JSONObject

object CatalogParser {

    fun parseCatalog(json: String): List<AppSummary> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("apps") ?: JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { add(parseSummary(it)) }
            }
        }
    }

    fun parseSummary(json: JSONObject): AppSummary {
        return AppSummary(
            slug = json.optString("slug"),
            name = json.optString("name"),
            tagline = json.optString("tagline"),
            developer = json.optString("developer"),
            category = json.optString("category", "Applications"),
            iconUrl = json.optString("iconUrl").takeIf { it.isNotBlank() },
            coverUrls = json.optJSONArray("coverUrls").toStringList(),
            accentColor = json.optString("accentColor", "#5B8CFF"),
            updatedAt = json.optString("updatedAt"),
            latestVersion = parseLatest(json.opt("latestVersion")),
            downloads = json.optLong("downloads", 0L),
        )
    }

    fun parseDetail(json: String): AppDetail {
        val objectJson = JSONObject(json)
        val summary = parseSummary(objectJson)
        val versionsArray = objectJson.optJSONArray("versions") ?: JSONArray()
        val versions = buildList {
            for (index in 0 until versionsArray.length()) {
                versionsArray.optJSONObject(index)?.let { add(parseVersion(it)) }
            }
        }
        return AppDetail(
            slug = summary.slug,
            name = summary.name,
            tagline = summary.tagline,
            developer = summary.developer,
            category = summary.category,
            iconUrl = summary.iconUrl,
            coverUrls = summary.coverUrls,
            accentColor = summary.accentColor,
            updatedAt = summary.updatedAt,
            downloads = summary.downloads,
            latestVersion = summary.latestVersion,
            description = objectJson.optString("description", summary.tagline),
            packageName = objectJson.optString("package"),
            createdAt = objectJson.optString("createdAt"),
            versions = versions,
        )
    }

    fun parseVersion(json: JSONObject): VersionInfo {
        return VersionInfo(
            version = json.optString("version"),
            versionCode = json.optInt("versionCode", 0),
            changelog = json.optString("changelog"),
            sizeBytes = json.optLong("sizeBytes", 0L),
            sha256 = json.optString("sha256"),
            publishedAt = json.optString("publishedAt"),
            downloadUrl = json.optString("downloadUrl"),
        )
    }

    private fun parseLatest(value: Any?): LatestVersion? {
        return when (value) {
            is JSONObject -> LatestVersion(
                version = value.optString("version"),
                versionCode = value.optInt("versionCode", 0),
                sizeBytes = value.optLong("sizeBytes", 0L),
                publishedAt = value.optString("publishedAt"),
            )
            is String -> value.takeIf { it.isNotBlank() }?.let {
                LatestVersion(version = it, versionCode = 0, sizeBytes = 0L, publishedAt = "")
            }
            else -> null
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }
}
