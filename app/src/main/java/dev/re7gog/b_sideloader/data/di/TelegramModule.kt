package dev.re7gog.b_sideloader.data.di

import android.content.Context
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.encrypt.SecureStorage
import org.drinkless.tdlib.Secrets
import org.drinkless.tdlib.TdApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {
    @Provides
    @Singleton
    fun provideTdlibParameters(@ApplicationContext context: Context): TdApi.SetTdlibParameters {
        val baseDir = context.filesDir.absolutePath
        val encryptionKey = SecureStorage(context).getOrGenerateDbKey()
        val apiId = Secrets.getApiId()
        val apiHash = Secrets.getApiHash()
        val locale = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
        val systemLanguageCode = locale?.language ?: "en"
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        val deviceModel = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
        return TdApi.SetTdlibParameters(
            false,
            "$baseDir/tdlib/db",
            "$baseDir/tdlib/files",
            encryptionKey,
            true,
            true,
            true,
            false,
            apiId,
            apiHash,
            systemLanguageCode,
            deviceModel,
            android.os.Build.VERSION.RELEASE,
            "1.0.0"
        )
    }
}
