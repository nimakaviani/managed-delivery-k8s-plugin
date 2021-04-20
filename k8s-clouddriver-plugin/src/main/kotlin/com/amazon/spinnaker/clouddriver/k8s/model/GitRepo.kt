package com.amazon.spinnaker.clouddriver.k8s.model

data class GitRepo(
    val name: String,
    val repositories: List<String>,
    val token: String = "",
    val userName: String = "",
    val password: String = "",
    var sshKey: String = ""
)