package com.amazon.spinnaker.keel.k8s.artifactSupplier

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import com.netflix.spinnaker.keel.artifacts.TagComparator

data class GitRepoVersionSortingStrategy(
    val strategy: TagVersionStrategy,
) : SortingStrategy {
    override val type: String = "git-repo-versions"
    private val tagComparator = TagComparator(strategy)

    override val comparator = Comparator<PublishedArtifact> { v1, v2 ->
        tagComparator.compare(v1.version, v2.version)
    }

    override fun toString(): String =
        "${javaClass.simpleName}[strategy=$strategy]}"
}