package dev.re7gog.b_sideloader.data.remote

import dev.re7gog.b_sideloader.data.remote.dto.GithubSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GithubApi {
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Header("User-Agent") userAgent: String = "B-SideLoader",
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("X-GitHub-Api-Version") apiVersion: String = "2022-11-28",
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): GithubSearchResponse
}