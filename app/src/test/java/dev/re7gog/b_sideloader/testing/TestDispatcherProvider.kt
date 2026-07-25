package dev.re7gog.b_sideloader.testing

import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Routes every dispatcher to one [TestDispatcher], so a test can advance the whole graph — flows,
 * ViewModels, repositories — with `runTest` and get deterministic ordering.
 *
 * This is the payoff of [DispatcherProvider] existing at all: without it the production code would
 * reference `Dispatchers.IO` directly and no test could control it.
 */
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

/**
 * Installs a [TestDispatcher] as `Dispatchers.Main` for the duration of a test.
 *
 * Required for anything touching `viewModelScope`, which is hard-wired to the main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
