package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitRepoVersionSortingStrategy
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.*

data class GitRepoArtifact(
    override var deliveryConfigName: String? = null,
    val namespace: String = "flux-system",
    val repoName: String,
    val project: String,
    val gitType: String,
    val tagVersionStrategy: TagVersionStrategy,
) : DeliveryArtifact() {
    override val name = "$GIT/$gitType/$project/$repoName"
    override val reference: String = name
    override val type = GIT

    @JsonIgnore
    override val statuses: Set<ArtifactStatus> = emptySet()

    override val sortingStrategy: SortingStrategy = GitRepoVersionSortingStrategy(tagVersionStrategy)
}

const val GIT: ArtifactType = "git"
