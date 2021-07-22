package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.FLUX_SOURCE_API_VERSION
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest

object FluxManifestUtil {
    fun generateGitRepoManifest(
        artifact: GitRepoArtifact,
        url: String,
        version: String?,
        environment: String? = null
    ): K8sObjectManifest {
        val girRepoManifestMetadata = mutableMapOf<String, Any?>(
            "namespace" to artifact.namespace
        )
        environment?.let {
            girRepoManifestMetadata["name"] = "${artifact.name}-$it"
        } ?: run {girRepoManifestMetadata["name"] = artifact.name}

        val gitRepoManifestSpec = mutableMapOf<String, Any?>(
            "interval" to artifact.interval,
            "url" to url
        )
        version?.let {
            gitRepoManifestSpec += ("ref" to mutableMapOf(
                "tag" to it
            ) )
        }
        artifact.secretRef?.let {
            gitRepoManifestSpec += ("secretRef" to mutableMapOf(
                "name" to it
            ) )
        }

        return K8sObjectManifest(
            FLUX_SOURCE_API_VERSION,
            artifact.kind,
            girRepoManifestMetadata,
            gitRepoManifestSpec
        )
    }
}
