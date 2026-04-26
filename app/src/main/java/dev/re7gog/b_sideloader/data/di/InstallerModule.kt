package dev.re7gog.b_sideloader.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.settings.SettingsManager
import dev.re7gog.b_sideloader.data.installer.InstallManager
import dev.re7gog.b_sideloader.domain.logic.IInstallManager
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InstallerModule {
    @Provides
    @Singleton
    fun provideInstallManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        settingsManager: SettingsManager
    ): IInstallManager {
        return InstallManager(context, okHttpClient, settingsManager)
    }
}