package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.FLUX_GIT_REPO_KIND
import com.amazon.spinnaker.keel.k8s.GIT
import com.amazon.spinnaker.keel.k8s.KUSTOMIZE_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.exception.NoGitVersionAvailable
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.model.KustomizeResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import mu.KotlinLogging

class GitVersionResolver(
    val repository: KeelRepository
) : Resolver<KustomizeResourceSpec> {

    override val supportedKind = KUSTOMIZE_RESOURCE_SPEC_V1
    private val logger = KotlinLogging.logger {}

    override fun invoke(resource: Resource<KustomizeResourceSpec>): Resource<KustomizeResourceSpec> {
        logger.debug("attempting to resolve resource for git")
        if (resource.spec.template.kind != FLUX_GIT_REPO_KIND) {
            return resource
        }
        val deliveryConfig = repository.deliveryConfigFor(resource.id)
        val environment = repository.environmentFor(resource.id)
        val spec = resource.spec.template.spec
        val tagRef = spec?.getOrElse("gitRef") {
            throw NoMatchingArtifactException(
                deliveryConfigName = deliveryConfig.name, type = GIT, reference = "notFound"
            )
        } as String

        logger.debug("tagRef: $tagRef")
        val artifact =  deliveryConfig.artifacts.find {
            logger.debug("checking $it")
            it.reference == tagRef && it.type == GIT
        } as GitRepoArtifact? ?: throw NoMatchingArtifactException( deliveryConfigName = deliveryConfig.name ,type = GIT,reference = tagRef)

        val version = repository.latestVersionApprovedIn(
            deliveryConfig, artifact, environment.name
        ) ?: throw NoGitVersionAvailable(artifact.name)
        logger.debug("found deployable version: $version")

        val sourceRef = mapOf<String, String>(
            "kind" to "GitRepository",
            "name" to artifact.name
        )
        spec.remove("gitRef")
        spec["sourceRef"] = sourceRef
        return resource
    }
}