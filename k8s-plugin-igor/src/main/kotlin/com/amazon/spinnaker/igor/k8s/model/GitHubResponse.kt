package com.amazon.spinnaker.igor.k8s.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubTagResponse(
    var name: String = "",
    var zipball_url: String = "",
    var tarball_url: String = "",
    var node_id: String = "",
    var commit: GitHubCommit = GitHubCommit()
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubCommit(
    var sha: String = "",
    var url: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubCommitResponse(
    var sha: String = "",
    var commit: Commit = Commit(),

    var htmlURL: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Commit (
    var author: CommitAuthor = CommitAuthor(),
    var message: String  = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
class CommitAuthor (
    var name: String = "",
    var email: String = "",
    var date: String = ""
)