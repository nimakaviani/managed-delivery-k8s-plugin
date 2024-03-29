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

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.InvalidArtifact
import com.amazon.spinnaker.keel.k8s.exception.NoVersionAvailable
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.model.KustomizeResourceSpec
import com.amazon.spinnaker.keel.k8s.resolver.FluxManifestUtil.generateGitRepoManifest
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.kork.exceptions.IntegrationException

class KustomizeResourceHandler(
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>,
    override val repository: KeelRepository
) : BaseFluxHandler<KustomizeResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers, repository
) {
    override val supportedKind = KUSTOMIZE_RESOURCE_SPEC_V1

    public override suspend fun toResolvedType(resource: Resource<KustomizeResourceSpec>): K8sObjectManifest {
        verifyKustomizeResource(resource)
        log.debug("attempting to resolve resource for git")

        val (artifact, deliveryConfig) = getArtifactAndConfig(resource)
        if (artifact == null) {
            resource.spec.template = toK8sList(
                null, resource.spec.template, generateCorrelationId(resource)
            )
            return super.toResolvedType(resource)
        }

        // Kustomize controller only supports GitRepository as source
        if (artifact !is GitRepoArtifact) {
            throw InvalidArtifact("provided artifact is not a GitRepository artifact.")
        }
        val spec = resource.spec.template.spec
        val environment = repository.environmentFor(resource.id)
        val version = repository.latestVersionApprovedIn(
            deliveryConfig, artifact, environment.name
        ) ?: throw NoVersionAvailable(artifact.name, artifact.type)

        log.debug("found deployable version: $version")
        val resolvedArtifact = resolveArtifactSpec(resource, artifact)
        val sourceRef = mapOf(
            KIND to resolvedArtifact.kind,
            NAME to "${resolvedArtifact.name}-${environment.name}",
            NAMESPACE to resolvedArtifact.namespace
        )
        spec?.set(FLUX_SOURCE_REF, sourceRef)

        val artifactFromKeelRepository = repository.getArtifactVersion(artifact, version, null)
        val repoUrl = artifactFromKeelRepository?.gitMetadata?.repo?.link
            ?: throw InvalidArtifact("artifact version $version does not have repository URL in artifact metadata")
        val gitRepoManifest = generateGitRepoManifest(resolvedArtifact, repoUrl, version, environment.name)

        resource.spec.template = toK8sList(
            gitRepoManifest,
            resource.spec.template,
            generateCorrelationId(resource)
        )

        // sending it to the super class for common labels and annotations to be added
        return super.toResolvedType(resource)
    }

    private fun verifyKustomizeResource(resource: Resource<KustomizeResourceSpec>) {
        resource.spec.template.spec?.let {
            KUSTOMIZE_REQUIRED_FIELDS.forEach { reqField ->
                if (!it.containsKey(reqField)) {
                    throw CannotResolveDesiredState(
                        resource.id,
                        IntegrationException("spec.template.spec.$reqField field is missing")
                    )
                }
            }
        } ?: throw CannotResolveDesiredState(resource.id, IntegrationException("spec.template.spec field is missing"))
    }

    override fun generateCorrelationId(resource: Resource<KustomizeResourceSpec>): String =
        "$K8S_LIST-$FLUX_KUSTOMIZE_KIND-${resource.spec.template.name()}"
}