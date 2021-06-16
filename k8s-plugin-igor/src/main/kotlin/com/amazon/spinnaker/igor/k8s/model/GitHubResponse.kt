package com.amazon.spinnaker.igor.k8s.model

data class GitHubTagResponse(
    val name: String,
    val zipball_url: String,
    val tarball_url: String,
    val node_id: String,
    val commit: GitHubCommit
)

data class GitHubCommit(
    val sha: String,
    val url: String
)