package com.amazon.spinnaker.igor.k8s.model

data class GitHubAccount(
    val name: String,
    val project: String,
    val url: String,
    val account: String? = null,
    val type: String = "github",
)
