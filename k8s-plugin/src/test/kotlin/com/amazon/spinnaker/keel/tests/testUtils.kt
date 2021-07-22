package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.FLUX_SOURCE_API_VERSION
import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.amazon.spinnaker.keel.k8s.K8S_LAST_APPLIED_CONFIG
import com.amazon.spinnaker.keel.k8s.K8sResourceModel
import com.amazon.spinnaker.keel.k8s.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.artifacts.NpmArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.normalize
import com.netflix.spinnaker.keel.jackson.mixins.ResourceSpecMixin
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.kork.exceptions.UserException

object testUtils {
    // cannot map a yaml string to DeliveryConfig directly because JsonTypeInfo is absent on resource property
    // mixin does not seem to work with configuredYamlMapper(). need to configure our own yaml mapper.
    // workaround until the artifact type change is implemented in keel
    fun SubmittedDeliveryConfig.makeDeliveryConfig() =
        DeliveryConfig(
            name = safeName,
            application = application,
            serviceAccount = serviceAccount
                ?: error("No service account specified, and no default applied"),
            artifacts = artifacts.mapTo(mutableSetOf()) { artifact ->
                when (artifact) {
                    is DebianArtifact -> artifact.copy(deliveryConfigName = safeName)
                    is DockerArtifact -> artifact.copy(deliveryConfigName = safeName)
                    is NpmArtifact -> artifact.copy(deliveryConfigName = safeName)
                    is GitRepoArtifact -> artifact.copy(deliveryConfigName = safeName)
                    else -> throw UserException("Unrecognized artifact sub-type: ${artifact.type} (${artifact.javaClass.name})")
                }
            },
            environments = environments.mapTo(mutableSetOf()) { env ->
                Environment(
                    name = env.name,
                    resources = env.resources.mapTo(mutableSetOf()) { resource ->
                        resource
                            .copy(metadata = mapOf("serviceAccount" to serviceAccount) + resource.metadata)
                            .normalize()
                    },
                    constraints = env.constraints,
                    verifyWith = env.verifyWith,
                    notifications = env.notifications,
                    postDeploy = env.postDeploy
                )
            },
            previewEnvironments = previewEnvironments,
            metadata = metadata ?: emptyMap()
        )

    fun generateYamlMapper(): ObjectMapper {
        val yamlMapper = configuredYamlMapper()
        yamlMapper.registerSubtypes(
            NamedType(GitRepoArtifact::class.java, "git"),
            NamedType(KustomizeResourceSpec::class.java, "k8s/kustomize@v1"),
            NamedType(HelmResourceSpec::class.java, "k8s/helm@v1")
        )
        yamlMapper.addMixIn(
            KustomizeResourceSpec::class.java, ResourceSpecMixin::class.java
        )
        yamlMapper.addMixIn(
            HelmResourceSpec::class.java, ResourceSpecMixin::class.java
        )
        return yamlMapper
    }

    fun gitRepositoryResourceModel(repoManifest: K8sObjectManifest): K8sResourceModel {
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = FLUX_SOURCE_API_VERSION,
                kind = FluxSupportedSourceType.GIT.fluxKind(),
                metadata = mutableMapOf(
                    "name" to "git-github-testProject-testRepo",
                    "annotations" to mapOf(
                        K8S_LAST_APPLIED_CONFIG to jacksonObjectMapper().writeValueAsString(repoManifest)
                    )
                ),
                spec = mutableMapOf<String, Any>() as K8sBlob
            ),
            metrics = emptyList(),
            moniker = null,
            name = "fnord",
            status = emptyMap(),
            warnings = emptyList()
        )
    }
}