package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider

data class K8sResourceSpec(
        val container: ContainerProvider?,
        val template: K8sObjectManifest,
        override val locations: SimpleLocations
) : ArtifactReferenceProvider, ResourceSpec, Locatable<SimpleLocations> {

    private val namespace: String = (template.metadata["namespace"] ?: "default") as String
    private val annotations = template.metadata["annotations"]
    private val appName =
        if (annotations != null) ((template.metadata["annotations"] as Map<String, String>)["moniker.spinnaker.io/application"]) else null

    override val application: String
        get() = (appName ?: "$namespace-$template.kind-${template.metadata["name"]}").toLowerCase()

    override val id: String
        get() = "$namespace-${template.kind}-${template.metadata["name"]}".toLowerCase()

    override val displayName: String
        get() = "$namespace-${template.kind}-${template.metadata["name"]}".toLowerCase()

    override val artifactReference: String
        get() = (container as ReferenceProvider).reference

    override val artifactType: ArtifactType?
        get() = DOCKER
}
