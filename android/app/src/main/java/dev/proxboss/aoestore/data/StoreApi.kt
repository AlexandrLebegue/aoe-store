package dev.proxboss.aoestore.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class StoreApi(private val client: OkHttpClient) {

    suspend fun health(baseUrl: String): Health {
        val payload = getJson(UrlMapper.health(baseUrl))
        return Health(
            status = payload.optString("status", "ok"),
            apps = payload.optInt("apps", 0),
        )
    }

    suspend fun catalog(baseUrl: String): CatalogPayload {
        val raw = get(UrlMapper.catalog(baseUrl))
        return CatalogPayload(CatalogParser.parseCatalog(raw), raw)
    }

    suspend fun appDetail(baseUrl: String, slug: String): AppDetail {
        return CatalogParser.parseDetail(get(UrlMapper.app(baseUrl, slug)))
    }

    private suspend fun getJson(url: String): JSONObject = JSONObject(get(url))

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw ApiException(
                    response.code,
                    message?.takeIf { it.isNotBlank() } ?: "Le serveur a répondu HTTP ${response.code}",
                )
            }
            body
        }
    }
}
