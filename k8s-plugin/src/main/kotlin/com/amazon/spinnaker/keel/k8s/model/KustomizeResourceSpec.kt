package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.docker.ReferenceProvider

class KustomizeResourceSpec (
    val chart: ReferenceProvider?,
    override val metadata: Map<String, String>,
    override val template: K8sObjectManifest,
    override val locations: SimpleLocations,
): GenericK8sLocatable {
    init {
        template.kind = template.kind ?: FLUX_KUSTOMIZE_KIND
        template.apiVersion = template.apiVersion ?: FLUX_KUSTOMIZE_API_VERSION
    }
}