package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.*
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider

data class K8sResourceSpec(
        val container: ContainerProvider?,
        val metadata: Map<String, String>,
        val template: K8sObjectManifest,
        override val locations: SimpleLocations
) : ArtifactReferenceProvider, ResourceSpec, Locatable<SimpleLocations> {

    private val namespace: String = (template.metadata[NAMESPACE] ?: NAMESPACE_DEFAULT) as String

    override val application: String
        get() = metadata.getValue(APPLICATION).toString()

    override val id: String
        get() = "$namespace-${template.kind}-${template.metadata[NAME]}".toLowerCase()

    override val displayName: String
        get() = "$namespace-${template.kind}-${template.metadata[NAME]}".toLowerCase()

    override val artifactReference: String?
        get() = if (container != null) (container as ReferenceProvider).reference else null

    override val artifactType: ArtifactType?
        get() = DOCKER
}
