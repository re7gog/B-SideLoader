package dev.re7gog.b_sideloader.domain.usecase

import dev.re7gog.b_sideloader.domain.device.DeviceInfo
import dev.re7gog.b_sideloader.domain.model.AppSource
import dev.re7gog.b_sideloader.domain.model.TrackedApp
import dev.re7gog.b_sideloader.domain.model.UpdateCandidate
import dev.re7gog.b_sideloader.domain.model.UpdateCheck
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import dev.re7gog.b_sideloader.domain.repository.TelegramRepository
import dev.re7gog.b_sideloader.domain.selection.GithubApkSelector
import dev.re7gog.b_sideloader.domain.selection.TelegramApkSelector
import javax.inject.Inject

/**
 * Resolves what a source currently offers for one app.
 *
 * This is the single place that knows how each source is queried and how a winner is picked, so
 * the details screen's "what would be installed" preview and the background sweep can never
 * disagree — a class of bug the old code had two independent copies of.
 *
 * Throws [dev.re7gog.b_sideloader.domain.error.AppError] when the source cannot be reached.
 */
class ResolveUpdateUseCase @Inject constructor(
    private val githubRepository: GithubRepository,
    private val telegramRepository: TelegramRepository,
    private val deviceInfo: DeviceInfo,
) {
    suspend operator fun invoke(app: TrackedApp): UpdateCheck =
        UpdateCheck(app = app, candidate = resolveCandidate(app))

    private suspend fun resolveCandidate(app: TrackedApp): UpdateCandidate? =
        when (val source = app.source) {
            is AppSource.GitHub -> GithubApkSelector.select(
                releases = githubRepository.getReleases(source.owner, source.repo),
                app = app,
                source = source,
                deviceAbis = deviceInfo.supportedAbis,
            )

            is AppSource.Telegram -> TelegramApkSelector.select(
                documents = telegramRepository.getApkDocuments(source.chatId, source.topicId),
                app = app,
                source = source,
                deviceAbis = deviceInfo.supportedAbis,
            )
        }
}

/**
 * Every APK the current filters accept, newest first.
 *
 * Backs the "available files" list on the details screens, which is what makes a filter mistake
 * visible before the user installs something wrong.
 */
class ListUpdateCandidatesUseCase @Inject constructor(
    private val githubRepository: GithubRepository,
    private val telegramRepository: TelegramRepository,
) {
    suspend operator fun invoke(app: TrackedApp): List<UpdateCandidate> =
        when (val source = app.source) {
            is AppSource.GitHub -> GithubApkSelector.matchingAssets(
                releases = githubRepository.getReleases(source.owner, source.repo),
                app = app,
                source = source,
            )

            is AppSource.Telegram -> TelegramApkSelector.filter(
                documents = telegramRepository.getApkDocuments(source.chatId, source.topicId),
                app = app,
                source = source,
            )
        }
}
