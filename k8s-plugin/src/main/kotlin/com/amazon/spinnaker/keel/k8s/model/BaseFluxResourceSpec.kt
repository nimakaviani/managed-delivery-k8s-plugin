package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType

abstract class BaseFluxResourceSpec:  ArtifactReferenceProvider, GenericK8sLocatable {
    abstract val artifactSpec: ArtifactSpec

    override val artifactReference: String
        get() = artifactSpec.ref

    override val artifactType: ArtifactType
        get() = FluxSupportedSourceType.GIT.name.toLowerCase()
}

// allow overriding things specified in BaseFluxArtifact per resource
data class ArtifactSpec(
    val ref: String,
    val namespace: String?,
    val interval: String?
)