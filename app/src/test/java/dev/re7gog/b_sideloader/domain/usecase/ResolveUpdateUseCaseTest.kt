package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.error.AppError
import dev.re7gog.b_sideloader.domain.model.AppVersion
import dev.re7gog.b_sideloader.domain.model.UpdateStatus
import dev.re7gog.b_sideloader.testing.FakeDeviceInfo
import dev.re7gog.b_sideloader.testing.FakeGithubRepository
import dev.re7gog.b_sideloader.testing.FakeTelegramRepository
import dev.re7gog.b_sideloader.testing.asset
import dev.re7gog.b_sideloader.testing.githubApp
import dev.re7gog.b_sideloader.testing.release
import dev.re7gog.b_sideloader.testing.telegramApp
import dev.re7gog.b_sideloader.testing.tgDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ResolveUpdateUseCaseTest {

    private val github = FakeGithubRepository()
    private val telegram = FakeTelegramRepository()
    private val useCase = ResolveUpdateUseCase(github, telegram, FakeDeviceInfo())

    @Test
    fun `github app resolves through the github repository`() = runTest {
        github.releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk"))))

        val check = useCase(githubApp())

        assertEquals("v1.0", check.candidate?.version?.raw)
    }

    @Test
    fun `telegram app resolves through the telegram repository`() = runTest {
        telegram.documents = listOf(tgDocument(7, "app.apk"))

        val check = useCase(telegramApp())

        assertEquals("7", check.candidate?.version?.raw)
    }

    @Test
    fun `status is NotInstalled when nothing has been installed yet`() = runTest {
        github.releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk"))))

        assertEquals(UpdateStatus.NotInstalled, useCase(githubApp()).status)
    }

    @Test
    fun `status is UpToDate when the installed version is the newest`() = runTest {
        github.releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk"))))

        val check = useCase(githubApp(version = AppVersion("v1.0")))

        assertEquals(UpdateStatus.UpToDate, check.status)
        assertEquals(false, check.hasUpdate)
    }

    @Test
    fun `status is UpdateAvailable when a newer version exists`() = runTest {
        github.releases = listOf(release("v2.0", assets = arrayOf(asset("app.apk"))))

        val check = useCase(githubApp(version = AppVersion("v1.0")))

        assertEquals(UpdateStatus.UpdateAvailable, check.status)
        assertEquals(true, check.hasUpdate)
    }

    @Test
    fun `status is NoCandidate when nothing passes the filters`() = runTest {
        github.releases = listOf(release("v1.0", assets = arrayOf(asset("app.apk"))))

        val check = useCase(githubApp(version = AppVersion("v1.0"), assetInclude = "nope"))

        assertEquals(UpdateStatus.NoCandidate, check.status)
        assertNull(check.candidate)
    }

    /** A source failure has to surface as a domain error, not be swallowed into "no update". */
    @Test
    fun `source failures propagate`() = runTest {
        github.failure = AppError.RateLimited()

        assertThrows(AppError.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { useCase(githubApp()) }
        }
    }
}
