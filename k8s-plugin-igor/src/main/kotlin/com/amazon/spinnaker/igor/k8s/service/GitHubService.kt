// Copyright 2022 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.spinnaker.igor.k8s.service

import com.amazon.spinnaker.igor.k8s.model.GitHubCommitResponse
import com.amazon.spinnaker.igor.k8s.model.GitHubTagResponse
import retrofit.http.GET
import retrofit.http.Path

interface GitHubService {
    @GET("/repos/{projectKey}/{repositorySlug}/tags")
    fun getTags(
        @Path("projectKey") projectKey: String,
        @Path("repositorySlug") repositorySlug: String
    ): List<GitHubTagResponse>

    @GET("/repos/{projectKey}/{repositorySlug}/commits/{commitId}")
    fun getCommit(
        @Path("projectKey") projectKey: String,
        @Path("repositorySlug") repositorySlug: String,
        @Path("commitId") commitId: String
    ): GitHubCommitResponse
}