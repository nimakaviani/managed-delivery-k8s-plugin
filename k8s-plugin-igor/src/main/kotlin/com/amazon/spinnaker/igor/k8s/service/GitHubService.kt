package com.amazon.spinnaker.igor.k8s.service

import com.amazon.spinnaker.igor.k8s.model.GitHubTagResponse
import retrofit.http.GET
import retrofit.http.Path

interface GitHubService {
    @GET("/repos/{projectKey}/{repositorySlug}/tags")
    fun getTags(
        @Path("projectKey") projectKey: String,
        @Path("repositorySlug") repositorySlug: String
    ): List<GitHubTagResponse>
}