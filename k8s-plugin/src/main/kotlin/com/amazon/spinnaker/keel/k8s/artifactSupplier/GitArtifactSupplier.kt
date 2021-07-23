// Copyright 2021 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.spinnaker.keel.k8s.artifactSupplier

import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.service.IgorArtifactServiceSupplier
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.*
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

    override val supportedArtifact = SupportedArtifact(FluxSupportedSourceType.GIT.name.toLowerCase(), GitRepoArtifact::class.java)
    override val supportedSortingStrategy =
        SupportedSortingStrategy(FluxSupportedSourceType.GIT.name.toLowerCase(), GitRepoVersionSortingStrategy::class.java)

    override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? {
        return runBlocking {
            if (artifact is GitRepoArtifact) {
                val versionFromIgor = igorArtifactService.getGitVersion(artifact.gitType, artifact.project, artifact.repoName, version )
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
                && artifact.type.toUpperCase() == FluxSupportedSourceType.GIT.name
                && artifact.metadata.isNotEmpty()
    }

    private fun findArtifactVersions(
        artifact: DeliveryArtifact
    ): Set<PublishedArtifact> =
        runBlocking {
            if (artifact is GitRepoArtifact) {
                val versionsFromIgor =
                    igorArtifactService.getGitVersions(artifact.gitType, artifact.project, artifact.repoName)
                return@runBlocking versionsFromIgor.map {
                    it.toPublishedArtifact(artifact.reference)
                }.toSet()
            }
            return@runBlocking emptySet()
        }

    override suspend fun getArtifactMetadata(artifact: PublishedArtifact): ArtifactMetadata {
        return ArtifactMetadata(
            null,
            artifact.gitMetadata ?: generateGitMetadata(artifact.metadata)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateGitMetadata(metadata: Map<String,Any?>): GitMetadata {
        val metadataMap = metadata as Map<String, String>
        return GitMetadata(
            metadataMap.getOrDefault("commitId", ""),
            metadataMap["author"],
            metadataMap["project"],
            metadataMap["branch"],
            Repo(
                metadataMap["name"],
                metadataMap["repoUrl"]
            ),
            null,
            Commit(
                metadataMap["commitId"],
                metadataMap["url"],
                metadataMap["message"]
            )
        )
    }
}