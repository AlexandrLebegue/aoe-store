package dev.proxboss.aoestore.data

data class LatestVersion(
    val version: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val publishedAt: String,
)

data class AppSummary(
    val slug: String,
    val name: String,
    val tagline: String,
    val developer: String,
    val category: String,
    val iconUrl: String?,
    val coverUrls: List<String>,
    val accentColor: String,
    val updatedAt: String,
    val latestVersion: LatestVersion?,
    val downloads: Long,
)

data class VersionInfo(
    val version: String,
    val versionCode: Int,
    val changelog: String,
    val sizeBytes: Long,
    val sha256: String,
    val publishedAt: String,
    val downloadUrl: String,
)

data class AppDetail(
    val slug: String,
    val name: String,
    val tagline: String,
    val developer: String,
    val category: String,
    val iconUrl: String?,
    val coverUrls: List<String>,
    val accentColor: String,
    val updatedAt: String,
    val downloads: Long,
    val latestVersion: LatestVersion?,
    val description: String,
    val packageName: String,
    val createdAt: String,
    val versions: List<VersionInfo>,
) {
    fun toSummary(): AppSummary = AppSummary(
        slug = slug,
        name = name,
        tagline = tagline,
        developer = developer,
        category = category,
        iconUrl = iconUrl,
        coverUrls = coverUrls,
        accentColor = accentColor,
        updatedAt = updatedAt,
        latestVersion = latestVersion,
        downloads = downloads,
    )
}

data class Health(val status: String, val apps: Int)

data class CatalogPayload(val apps: List<AppSummary>, val rawJson: String)

class ApiException(val statusCode: Int, message: String) : Exception(message)

class ChecksumMismatchException(message: String) : Exception(message)
