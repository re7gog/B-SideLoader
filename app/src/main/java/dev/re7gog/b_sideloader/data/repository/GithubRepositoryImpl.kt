package dev.re7gog.b_sideloader.data.repository

import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.data.error.apiCall
import dev.re7gog.b_sideloader.data.remote.api.GithubApi
import dev.re7gog.b_sideloader.data.remote.mapper.toDomain
import dev.re7gog.b_sideloader.domain.model.GithubRelease
import dev.re7gog.b_sideloader.domain.model.GithubRepoSummary
import dev.re7gog.b_sideloader.domain.repository.GithubRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit-backed [GithubRepository]: fetch, map, translate errors. No selection logic — which
 * release wins is [dev.re7gog.b_sideloader.domain.selection.GithubApkSelector]'s job.
 */
@Singleton
class GithubRepositoryImpl @Inject constructor(
    private val api: GithubApi,
    private val dispatchers: DispatcherProvider,
) : GithubRepository {

    override suspend fun searchRepositories(query: String, page: Int?): List<GithubRepoSummary> =
        withContext(dispatchers.io) {
            if (query.isBlank()) return@withContext emptyList()
            apiCall { api.searchRepositories(query = query, page = page) }
                .items
                .map { it.toDomain() }
        }

    override suspend fun getRepository(owner: String, repo: String): GithubRepoSummary =
        withContext(dispatchers.io) {
            apiCall { api.getRepository(owner, repo) }.toDomain()
        }

    override suspend fun getReleases(owner: String, repo: String, page: Int?): List<GithubRelease> =
        withContext(dispatchers.io) {
            apiCall { api.getReleases(owner, repo, page) }.toDomain()
        }
}
