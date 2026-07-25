package dev.re7gog.b_sideloader.data.di

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.core.os.ConfigurationCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.BuildConfig
import dev.re7gog.b_sideloader.data.device.AndroidDeviceInfo
import dev.re7gog.b_sideloader.data.encrypt.SecureSecretsRepository
import org.drinkless.tdlib.Secrets
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {

    /**
     * TDLib's start-up parameters.
     *
     * The API id/hash are reconstructed in native code at runtime (see `tdlib/src/main/cpp`) so
     * they are not sitting in the DEX as plain constants, and the database key comes from the
     * hardware Keystore. The app version is taken from `BuildConfig` rather than a hand-written
     * literal, which used to drift from the real version on every release.
     */
    @Provides
    @Singleton
    fun provideTdlibParameters(
        @ApplicationContext context: Context,
        secrets: SecureSecretsRepository,
        deviceInfo: AndroidDeviceInfo,
    ): TdApi.SetTdlibParameters {
        val baseDir = context.filesDir.absolutePath
        val systemLanguage = ConfigurationCompat
            .getLocales(Resources.getSystem().configuration)[0]
            ?.language
            ?: DEFAULT_LANGUAGE

        return TdApi.SetTdlibParameters(
            /* useTestDc = */ false,
            /* databaseDirectory = */ baseDir + File.separator + TDLIB_DB_DIR,
            /* filesDirectory = */ baseDir + File.separator + TDLIB_FILES_DIR,
            /* databaseEncryptionKey = */ secrets.getOrCreateTelegramDbKey(),
            /* useFileDatabase = */ true,
            /* useChatInfoDatabase = */ true,
            /* useMessageDatabase = */ true,
            /* useSecretChats = */ false,
            /* apiId = */ Secrets.getApiId(),
            /* apiHash = */ Secrets.getApiHash(),
            /* systemLanguageCode = */ systemLanguage,
            /* deviceModel = */ deviceInfo.displayName,
            /* systemVersion = */ Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            /* applicationVersion = */ BuildConfig.VERSION_NAME,
        )
    }

    private const val DEFAULT_LANGUAGE = "en"
    private const val TDLIB_DB_DIR = "tdlib/db"
    private const val TDLIB_FILES_DIR = "tdlib/files"
}
