package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitRepoVersionSortingStrategy
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.*

data class GitRepoArtifact(
    val account: String? = null,
    val chartName: String,
    override val deliveryConfigName: String? = null,
    override val name: String,
    val namespace: String? = null,
    override val reference: String = name,
    override val type: ArtifactType,
    val url: String,
    val tagVersionStrategy: TagVersionStrategy,
) : DeliveryArtifact() {

    @JsonIgnore
    override val statuses: Set<ArtifactStatus> = emptySet()

    override val sortingStrategy: SortingStrategy = GitRepoVersionSortingStrategy(tagVersionStrategy)
}