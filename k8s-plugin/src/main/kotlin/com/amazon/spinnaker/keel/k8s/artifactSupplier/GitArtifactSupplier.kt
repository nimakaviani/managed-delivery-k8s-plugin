package com.amazon.spinnaker.keel.k8s.artifactSupplier

import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.service.IgorArtifactServiceSupplier
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.BaseArtifactSupplier
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import kotlinx.coroutines.runBlocking

class GitArtifactSupplier (
    override val artifactMetadataService: ArtifactMetadataService,
    override val eventPublisher: EventPublisher,
    private val igorArtifactService: IgorArtifactServiceSupplier
) : BaseArtifactSupplier<GitRepoArtifact, GitRepoVersionSortingStrategy>(artifactMetadataService) {

    override val supportedArtifact = SupportedArtifact("git", GitRepoArtifact::class.java)
    override val supportedSortingStrategy =
        SupportedSortingStrategy("git", GitRepoVersionSortingStrategy::class.java)

    override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? {
        return runBlocking {
            if (artifact is GitRepoArtifact) {
                val versionFromIgor = igorArtifactService.getGtiVersion(artifact.gitType, artifact.project, artifact.repoName, version )
                return@runBlocking versionFromIgor.toPublishedArtifact(artifact.reference)
            }
            null
        }
    }

    override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? {
        return runBlocking {
            findArtifactVersions(artifact).sortedWith(artifact.sortingStrategy.comparator).firstOrNull()
        }
    }

    override fun getLatestArtifacts(
        deliveryConfig: DeliveryConfig,
        artifact: DeliveryArtifact,
        limit: Int
    ): List<PublishedArtifact> {
        return findArtifactVersions(artifact).sortedWith(artifact.sortingStrategy.comparator).take(limit)
    }

    override fun shouldProcessArtifact(artifact: PublishedArtifact): Boolean {
        return artifact.version.isNotBlank()
    }

    private fun findArtifactVersions(
        artifact: DeliveryArtifact
    ): Set<PublishedArtifact> {
        val result = mutableSetOf<PublishedArtifact>()
        if (artifact is GitRepoArtifact) {
            runBlocking {
                val versionsFromIgor = igorArtifactService.getGtiVersions(artifact.gitType, artifact.project, artifact.repoName)
                versionsFromIgor.forEach {
                    result.add(it.toPublishedArtifact(artifact.reference))
                }
            }
        }
        return result
    }
}