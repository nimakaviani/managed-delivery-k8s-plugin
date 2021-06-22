package com.amazon.spinnaker.igor.k8s.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommit(
    @JsonProperty("sha")
    val sha: String,
    @JsonProperty("url")
    val url: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommitResponse(
    @JsonProperty("sha")
    val sha: String,
    @JsonProperty("commit")
    val commit: Commit,

    @JsonProperty("html_url")
    val htmlURL: String,
) {
    fun toMetaData(): Map<String, String> {
        return mapOf(
            "commitId" to sha,
            "url" to htmlURL,
            "date" to commit.author.date,
            "author" to commit.author.name,
            "email" to commit.author.email,
            "message" to commit.message
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Commit (
    @JsonProperty("author")
    val author: CommitAuthor,
    @JsonProperty("message")
    val message: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommitAuthor (
    @JsonProperty("name")
    val name: String,
    @JsonProperty("email")
    val email: String,
    @JsonProperty("date")
    val date: String
)