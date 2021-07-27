package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType

abstract class BaseFluxResourceSpec:  ArtifactReferenceProvider, GenericK8sLocatable {
    abstract val artifactRef: String

    override val artifactReference: String
        get() = artifactRef

    override val artifactType: ArtifactType
        get() = FluxSupportedSourceType.GIT.name.toLowerCase()
}