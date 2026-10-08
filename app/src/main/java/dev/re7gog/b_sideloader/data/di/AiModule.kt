package dev.re7gog.b_sideloader.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.data.ai.CloudModel
import dev.re7gog.b_sideloader.data.ai.GeminiNanoModel
import dev.re7gog.b_sideloader.data.ai.LanguageModelGatewayImpl
import dev.re7gog.b_sideloader.data.ai.OkHttpCloudModel
import dev.re7gog.b_sideloader.data.ai.OnDeviceModel
import dev.re7gog.b_sideloader.domain.ai.LanguageModelGateway
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindLanguageModelGateway(impl: LanguageModelGatewayImpl): LanguageModelGateway

    @Binds
    @Singleton
    abstract fun bindOnDeviceModel(impl: GeminiNanoModel): OnDeviceModel

    @Binds
    @Singleton
    abstract fun bindCloudModel(impl: OkHttpCloudModel): CloudModel

    companion object {
        @Provides
        @Singleton
        @AiHttpClient
        fun provideAiHttpClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
            .apply { interceptors().clear() }
            // A thinking model can be silent for a while before the first byte of its answer.
            .readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        private const val AI_READ_TIMEOUT_SECONDS = 120L
    }
}
