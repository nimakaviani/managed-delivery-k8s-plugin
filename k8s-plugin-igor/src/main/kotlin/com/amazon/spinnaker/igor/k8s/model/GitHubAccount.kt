package com.amazon.spinnaker.igor.k8s.model

data class GitHubAccount (
    val type: String = "github",
    val name: String,
    val project: String,
    val account: String? = null
)
