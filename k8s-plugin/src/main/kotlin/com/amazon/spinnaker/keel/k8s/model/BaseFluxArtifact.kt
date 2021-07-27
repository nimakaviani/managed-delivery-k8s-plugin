package com.amazon.spinnaker.keel.k8s.model

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

abstract class BaseFluxArtifact: DeliveryArtifact() {
    abstract val kind: String
    abstract val secretRef: String?
    abstract val interval: String
    open val namespace: String = "flux-system"
}