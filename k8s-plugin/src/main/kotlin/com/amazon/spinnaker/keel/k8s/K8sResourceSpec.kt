package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations

typealias K8sSpec = MutableMap<String, Any?>

data class K8sResourceSpec(
    val template: K8sResourceTemplate,
    override val locations: SimpleLocations
) : ResourceSpec, Locatable<SimpleLocations> {

    val namespace: String = (template.metadata["namespace"] ?: "default") as String

    private val annotations = template.metadata["annotations"]
    private val appName =
        if (annotations != null) ((template.metadata["annotations"] as Map<String, String>)["moniker.spinnaker.io/application"]) else null

    override val application: String
        get() = (appName ?: "$namespace-$template.kind-${template.metadata["name"]}")

    override val id: String
        get() = "$namespace-${template.kind}-${template.metadata["name"]}"

    override val displayName: String
        get() = "$namespace-${template.kind}-${template.metadata["name"]}"

    fun name(): String {
        return "${template.kind}-${(template.metadata["name"] as String)}"
    }
}

data class K8sResourceTemplate(
    val apiVersion: String,
    val kind: String,
    val spec: K8sSpec,
    val metadata: Map<String, Any?>
)
