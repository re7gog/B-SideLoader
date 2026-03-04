package dev.re7gog.b_sideloader.data.remote

import dev.re7gog.b_sideloader.data.remote.dto.GithubReleaseDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubRepoDto
import dev.re7gog.b_sideloader.data.remote.dto.GithubSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

const val USER_AGENT = "B-SideLoader"
const val ACCEPT = "application/vnd.github+json"
const val API_VERSION = "2022-11-28"

interface GithubApi {
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Accept") accept: String = ACCEPT,
        @Header("X-GitHub-Api-Version") apiVersion: String = API_VERSION,
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): GithubSearchResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Accept") accept: String = ACCEPT,
        @Header("X-GitHub-Api-Version") apiVersion: String = API_VERSION,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GithubRepoDto

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Accept") accept: String = ACCEPT,
        @Header("X-GitHub-Api-Version") apiVersion: String = API_VERSION,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1
    ): List<GithubReleaseDto>
}