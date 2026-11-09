package dev.proxboss.aoestore

import android.app.Application
import android.content.Context
import dev.proxboss.aoestore.data.ApkDownloader
import dev.proxboss.aoestore.data.SettingsStore
import dev.proxboss.aoestore.data.StoreApi
import dev.proxboss.aoestore.data.StoreRepository
import dev.proxboss.aoestore.install.ApkInstaller
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class StoreApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val settings = SettingsStore(appContext)
    val api = StoreApi(okHttpClient)
    val repository = StoreRepository(api, settings)
    val downloader = ApkDownloader(okHttpClient, appContext)
    val installer = ApkInstaller(appContext)
}
