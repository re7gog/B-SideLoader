package dev.re7gog.b_sideloader.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.background.AndroidBackgroundRestrictions
import dev.re7gog.b_sideloader.data.background.WorkManagerBackgroundScheduler
import dev.re7gog.b_sideloader.data.device.AndroidDeviceInfo
import dev.re7gog.b_sideloader.data.device.AndroidSelfAppInfo
import dev.re7gog.b_sideloader.data.encrypt.SecureSecretsRepository
import dev.re7gog.b_sideloader.data.installer.AndroidPackageInspector
import dev.re7gog.b_sideloader.data.installer.CacheApkStagingArea
import dev.re7gog.b_sideloader.data.installer.InstallerGatewayImpl
import dev.re7gog.b_sideloader.data.remote.interceptor.AuthTokenSource
import dev.re7gog.b_sideloader.data.repository.GithubRepositoryImpl
import dev.re7gog.b_sideloader.data.repository.RoomAppsRepository
import dev.re7gog.b_sideloader.data.settings.DataStorePendingSelfUpdateRepository
import dev.re7gog.b_sideloader.data.settings.DataStoreSettingsRepository
import dev.re7gog.b_sideloader.data.telegram.TelegramRepositoryImpl
import dev.re7gog.b_sideloader.domain.background.BackgroundRestrictions
import dev.re7gog.b_sideloader.domain.background.BackgroundWorkScheduler
import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.device.SelfAppInfo
import dev.re7gog.b_sideloader.domain.installer.ApkStagingArea
import dev.re7gog.b_sideloader.domain.installer.InstallerGateway
import dev.re7gog.b_sideloader.domain.installer.PackageInspector
import dev.re7gog.b_sideloader.domain.repository.AppsRepository
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.PendingSelfUpdateRepository
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import dev.re7gog.b_sideloader.domain.repository.SettingsRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import javax.inject.Singleton

/**
 * Domain port -> data adapter. Every binding here is the seam that lets a test swap in a fake:
 * nothing above the data layer names an implementation class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppsRepository(impl: RoomAppsRepository): AppsRepository

    @Binds
    @Singleton
    abstract fun bindGithubRepository(impl: GithubRepositoryImpl): GithubRepository

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(impl: TelegramRepositoryImpl): TelegramRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPendingSelfUpdateRepository(
        impl: DataStorePendingSelfUpdateRepository,
    ): PendingSelfUpdateRepository

    @Binds
    @Singleton
    abstract fun bindSecretsRepository(impl: SecureSecretsRepository): SecretsRepository

    /** Same instance as the repository above: the token cache must not be duplicated. */
    @Binds
    @Singleton
    abstract fun bindAuthTokenSource(impl: SecureSecretsRepository): AuthTokenSource

    @Binds
    @Singleton
    abstract fun bindInstallerGateway(impl: InstallerGatewayImpl): InstallerGateway

    @Binds
    @Singleton
    abstract fun bindPackageInspector(impl: AndroidPackageInspector): PackageInspector

    @Binds
    @Singleton
    abstract fun bindApkStagingArea(impl: CacheApkStagingArea): ApkStagingArea

    @Binds
    @Singleton
    abstract fun bindDeviceInfo(impl: AndroidDeviceInfo): DeviceInfo

    @Binds
    @Singleton
    abstract fun bindSelfAppInfo(impl: AndroidSelfAppInfo): SelfAppInfo

    @Binds
    @Singleton
    abstract fun bindBackgroundScheduler(impl: WorkManagerBackgroundScheduler): BackgroundWorkScheduler

    @Binds
    @Singleton
    abstract fun bindBackgroundRestrictions(impl: AndroidBackgroundRestrictions): BackgroundRestrictions
}
