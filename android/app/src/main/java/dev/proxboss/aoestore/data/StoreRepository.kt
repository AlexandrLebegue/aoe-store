package dev.proxboss.aoestore.data

class StoreRepository(
    private val api: StoreApi,
    private val settings: SettingsStore,
) {

    suspend fun cachedCatalog(baseUrl: String): List<AppSummary>? {
        val raw = settings.cachedCatalog(baseUrl) ?: return null
        return runCatching { CatalogParser.parseCatalog(raw) }.getOrNull()
    }

    suspend fun refreshCatalog(baseUrl: String): List<AppSummary> {
        val payload = api.catalog(baseUrl)
        settings.cacheCatalog(baseUrl, payload.rawJson)
        return payload.apps
    }

    suspend fun appDetail(baseUrl: String, slug: String): AppDetail = api.appDetail(baseUrl, slug)

    suspend fun health(baseUrl: String): Health = api.health(baseUrl)
}
