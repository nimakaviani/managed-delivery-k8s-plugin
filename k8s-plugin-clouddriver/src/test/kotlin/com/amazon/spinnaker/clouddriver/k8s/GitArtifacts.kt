package com.amazon.spinnaker.clouddriver.k8s

import com.amazon.spinnaker.clouddriver.k8s.model.GitRepo

data class GitArtifacts(
    var accounts: MutableList<GitRepo> = mutableListOf(),
    var enabled: Boolean? = false
)
