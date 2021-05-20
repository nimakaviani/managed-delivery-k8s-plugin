package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.SimpleLocations

interface GenericK8sLocatable : Locatable<SimpleLocations> {
    val metadata: Map<String, String>
    val template: K8sManifest?
    val namespace: String
        get() = (template?.metadata?.get(NAMESPACE) ?: NAMESPACE_DEFAULT) as String

    override val application: String
        get() = metadata.getValue(APPLICATION).toString()

    override val id: String
        get() = "${locations.account}-$namespace-${template?.kind}-${template?.name()}".toLowerCase()

    override val displayName: String
        get() = "${locations.account}-$namespace-${template?.kind}-${template?.name()}".toLowerCase()
}