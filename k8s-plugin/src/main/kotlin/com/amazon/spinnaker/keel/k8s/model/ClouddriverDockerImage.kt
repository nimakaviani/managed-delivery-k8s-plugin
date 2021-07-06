package com.amazon.spinnaker.keel.k8s.model

data class ClouddriverDockerImage(
    val account: String,
    val artifact: Artifact,
    val digest: String?,
    val registry: String,
    val repository: String,
    val tag: String
)

data class Artifact(
    val metadata: Metadata,
    val name: String,
    val reference: String,
    val type: String,
    val version: String
)

data class Metadata(
    val labels: Map<String, String>,
    val registry: String
)