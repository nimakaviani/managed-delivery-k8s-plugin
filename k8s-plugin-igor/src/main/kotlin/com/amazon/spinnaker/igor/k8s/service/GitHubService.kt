package com.amazon.spinnaker.igor.k8s.service

import com.amazon.spinnaker.igor.k8s.model.GitHubTagResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubService {
    @GET("/repos/{projectKey}/{repositorySlug}/tags")
    suspend fun getTags(
        @Path("projectKey") projectKey: String,
        @Path("repositorySlug") repositorySlug: String
    ): GitHubTagResponse
}