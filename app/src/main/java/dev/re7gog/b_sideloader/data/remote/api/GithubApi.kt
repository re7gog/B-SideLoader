package dev.re7gog.b_sideloader.data.remote.api

import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The GitHub REST endpoints this app uses.
 *
 * Note what is *not* here: `User-Agent`, `Accept`, `X-GitHub-Api-Version` and `Authorization` used
 * to be `@Header` parameters repeated on every method, which meant every call site had to
 * remember to pass the token and one that forgot silently ran unauthenticated (and hit the 60
 * req/h anonymous rate limit). They are now OkHttp interceptors, so they cannot be forgotten and
 * cannot leak into a log — see `data/remote/interceptor/`.
 */
interface GithubApi {

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int = DEFAULT_PAGE_SIZE,
    ): GithubSearchResponseDto

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GithubRepoDto

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int = DEFAULT_PAGE_SIZE,
    ): List<GithubReleaseDto>

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val DEFAULT_PAGE_SIZE = 30
    }
}
