package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.schema.Optional
import javax.websocket.ContainerProvider

typealias K8sSpec = MutableMap<String, Any?>

data class K8sResourceSpec(
    val container: ContainerProvider?,
    val template: K8sResourceTemplate,
    override val locations: SimpleLocations
) : ResourceSpec, Locatable<SimpleLocations> {

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


}

data class K8sResourceTemplate(
    val apiVersion: String,
    val kind: String,
    val metadata: Map<String, Any?>,
    val spec: K8sSpec
) {
    val namespace: String = (metadata["namespace"] ?: "default") as String
    val name: String = if (metadata["name"] != null) ("${kind}-${(metadata["name"] as String)}".toLowerCase()) else ""
}
