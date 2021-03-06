package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.*
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider

data class K8sResourceSpec(
    val container: ContainerProvider?,
    override val metadata: Map<String, String>,
    override val template: K8sObjectManifest,
    override val locations: SimpleLocations
) : ArtifactReferenceProvider, GenericK8sLocatable {

    override val artifactReference: String?
        get() = if (container != null) (container as ReferenceProvider).reference else null

    override val artifactType: ArtifactType?
        get() = DOCKER
}
