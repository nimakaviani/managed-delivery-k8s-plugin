package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.K8sCredentialManifest
import com.amazon.spinnaker.keel.k8s.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.SECRET
import com.amazon.spinnaker.keel.k8s.SECRET_API_V1
import com.netflix.spinnaker.keel.api.SimpleLocations

data class CredentialsResourceSpec(
    override val locations: SimpleLocations,
    override val metadata: Map<String, String>,
    override val template: K8sCredentialManifest,
) : GenericK8sLocatable {
    init {
        template.kind = template.kind ?: SECRET
        template.apiVersion = template.apiVersion ?: SECRET_API_V1
    }
}
