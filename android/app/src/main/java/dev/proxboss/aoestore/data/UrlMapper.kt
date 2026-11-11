package dev.proxboss.aoestore.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlMapper {

    const val DEFAULT_SERVER = "http://192.168.1.63:8099"

    fun normalizeBase(input: String): String {
        var value = input.trim()
        if (value.isEmpty()) return DEFAULT_SERVER
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://$value"
        }
        return value.trimEnd('/')
    }

    fun resolve(baseUrl: String, pathOrUrl: String?): String? {
        val path = pathOrUrl?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = normalizeBase(baseUrl)
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    fun health(baseUrl: String): String = "${normalizeBase(baseUrl)}/api/health"

    fun catalog(baseUrl: String): String = "${normalizeBase(baseUrl)}/api/apps"

    fun app(baseUrl: String, slug: String): String =
        "${normalizeBase(baseUrl)}/api/apps/${encodeSegment(slug)}"

    fun download(baseUrl: String, slug: String, version: String): String =
        "${normalizeBase(baseUrl)}/api/apps/${encodeSegment(slug)}/versions/${encodeSegment(version)}/download"

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
