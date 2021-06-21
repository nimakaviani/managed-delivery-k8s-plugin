package com.amazon.spinnaker.igor.k8s.model

import com.fasterxml.jackson.annotation.JsonProperty

data class GitHubTagResponse(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("zipball_url")
    val zipball_url: String,
    @JsonProperty("tarball_url")
    val tarball_url: String,
    @JsonProperty("node_id")
    val node_id: String,
    @JsonProperty("commit")
    val commit: GitHubCommit
)

data class GitHubCommit(
    @JsonProperty("sha")
    val sha: String,
    @JsonProperty("url")
    val url: String
)