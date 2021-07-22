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

package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.FLUX_HELM_KIND
import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.amazon.spinnaker.keel.k8s.HELM_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.K8S_LIST
import com.amazon.spinnaker.keel.k8s.exception.InvalidArtifact
import com.amazon.spinnaker.keel.k8s.exception.NoVersionAvailable
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sManifest
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.resolver.FluxManifestUtil.generateGitRepoManifest
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException

class HelmResourceHandler(
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>,
    val repository: KeelRepository
) : GenericK8sResourceHandler<HelmResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers
) {
    override val supportedKind = HELM_RESOURCE_SPEC_V1

    @Suppress("UNCHECKED_CAST")
    public override suspend fun toResolvedType(resource: Resource<HelmResourceSpec>): K8sObjectManifest {

        val (artifact, deliveryConfig) = getArtifactAndConfig(resource)
        val environment = repository.environmentFor(resource.id)
        val version = repository.latestVersionApprovedIn(
            deliveryConfig, artifact, environment.name
        ) ?: throw NoVersionAvailable(artifact.name, artifact.type)

        val artifactFromKeelRepository = repository.getArtifactVersion(artifact, version, null)

        when (artifact) {
            // flux ignores the version field when GitRepository or Bucket is used. Must specify version at source controller
            is GitRepoArtifact -> {
                val sourceRef = mutableMapOf(
                    "name" to "${artifact.name}-${environment.name}",
                    "kind" to artifact.kind,
                    "namespace" to artifact.namespace
                )
                resource.spec.template.spec?.let {
                    val chartSpec = (it["chart"] as MutableMap<String, Any>)["spec"] as MutableMap<String, Any>
                    chartSpec["sourceRef"] = sourceRef
                }

                val repoUrl = artifactFromKeelRepository?.gitMetadata?.repo?.link
                    ?: throw InvalidArtifact("artifact version $version does not have repository URL in artifact metadata")
                val gitRepoManifest = generateGitRepoManifest(artifact, repoUrl, version, environment.name)
                resource.spec.template = toK8sList(
                    gitRepoManifest, resource.spec.template, generateCorrelationId(resource)
                )
            }
            // TODO expand supported artifact kinds
            else -> throw InvalidArtifact("artifact version $version is not a supported artifact type. artifact: $artifact")
        }

        resource.spec.template.spec

        // sending it to the super class for common labels and annotations to be added
        return super.toResolvedType(resource)
    }

    override suspend fun current(resource: Resource<HelmResourceSpec>): K8sObjectManifest? {
        val deployed = getK8sResource(resource)
        notifyHealthAndArtifactDeployment(deployed, resource)
        return deployed
    }

    override suspend fun getK8sResource(r: Resource<HelmResourceSpec>): K8sObjectManifest? {
        val (artifact, _) = getArtifactAndConfig(r)
        val environment: String? = when (artifact) {
            is GitRepoArtifact -> {
                repository.environmentFor(r.id).name
            }
            else -> {
                null
            }
        }
        return super.getFluxK8sResources(r, artifact, generateCorrelationId(r), environment)
    }

    override suspend fun actuationInProgress(resource: Resource<HelmResourceSpec>): Boolean =
        resource
            .spec.template.let {
                orcaService.getCorrelatedExecutions(generateCorrelationId(resource)).isNotEmpty()
            }

    private fun getArtifactAndConfig(resource: Resource<HelmResourceSpec>): Pair<DeliveryArtifact, DeliveryConfig> {
        val deliveryConfig = repository.deliveryConfigFor(resource.id)
        resource.spec.artifactRef.let { artifactRef ->
            val artifact = deliveryConfig.artifacts.find {
                it.reference == artifactRef
            } ?: throw NoMatchingArtifactException(
                deliveryConfigName = deliveryConfig.name,
                type = "unknown",
                reference = artifactRef
            )
            return Pair(artifact, deliveryConfig)
        }
    }

    override suspend fun upsert(
        resource: Resource<HelmResourceSpec>,
        diff: ResourceDiff<K8sObjectManifest>
    ): List<Task> {
        val spec = (diff.desired)
        spec.items?.forEach { manifest ->
            when (manifest.kind) {
                FluxSupportedSourceType.GIT.fluxKind() -> {
                    findAndNotifyGitArtifactDeployment(manifest, resource, "tag")
                }
                //TODO add support for other artifact types
            }
        } ?: log.warn("generated resource does not have anything under items field. Please check your configuration")
        return super.upsert(resource, diff)
    }

    private fun findAndNotifyGitArtifactDeployment(
        manifest: K8sManifest,
        resource: Resource<HelmResourceSpec>,
        versionString: String
    ) {
        val version = find(manifest.spec ?: mutableMapOf(), versionString) as String?
        log.debug("found tag: $version")
        version?.let {
            log.info("Deploying Git artifact $it")
            notifyArtifactDeploying(resource, it)
        }
    }

    private fun generateCorrelationId(resource: Resource<HelmResourceSpec>): String =
        "$K8S_LIST-$FLUX_HELM_KIND-${resource.spec.template.name()}"
}