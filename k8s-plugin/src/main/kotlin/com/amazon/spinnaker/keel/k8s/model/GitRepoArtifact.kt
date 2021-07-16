package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.GIT
import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitRepoVersionSortingStrategy
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.*

data class GitRepoArtifact(
    override var deliveryConfigName: String? = null,
    override val reference: String,
    val repoName: String,
    val project: String,
    val gitType: String,
    val tagVersionStrategy: TagVersionStrategy,
    val namespace: String = "flux-system",
    val interval: String = "1m",
    val secretRef: String? = null,
) : DeliveryArtifact() {
    override val name = "$GIT-$gitType-$project-$repoName"
    override val type = GIT

    @JsonIgnore
    override val statuses: Set<ArtifactStatus> = emptySet()

    override val sortingStrategy: SortingStrategy = GitRepoVersionSortingStrategy(tagVersionStrategy)
}
