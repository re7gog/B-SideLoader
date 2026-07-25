package dev.re7gog.b_sideloader.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.re7gog.b_sideloader.core.coroutines.DefaultDispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.log.AndroidLogger
import dev.re7gog.b_sideloader.core.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindingsModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger
}

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    /**
     * A scope that lives as long as the process.
     *
     * `SupervisorJob` so one failed child (a broadcast handler, a shared flow) cannot tear down
     * the others. Deliberately singular and injected: the code it replaces created a fresh
     * `CoroutineScope(...)` inside receivers and managers, which nothing owned and nothing could
     * cancel.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
