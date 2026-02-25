package dev.re7gog.b_sideloader.data.remote

import dev.re7gog.b_sideloader.data.remote.dto.GithubSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GithubApi {
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String = "stars",
        @Header("User-Agent") userAgent: String = "B-SideLoader"
    ): GithubSearchResponse
}