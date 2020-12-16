package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations

typealias SpecType = MutableMap<String, Any?>

data class K8sResourceSpec(
    val apiVersion: String,
    val kind: String,
    val spec: SpecType,
    val metadata: Map<String, Any?>,
    override val locations: SimpleLocations
) : ResourceSpec, Locatable<SimpleLocations> {

    val namespace: String = (metadata["namespace"] ?: "default") as String

    private val annotations = metadata["annotations"]
    private val appName =
        if (annotations != null) ((metadata["annotations"] as Map<String, String>)["moniker.spinnaker.io/application"]) else null

    override val application: String
        get() = (appName ?: "$namespace-$kind-${metadata["name"]}")

    override val id: String
        get() = "$namespace-$kind-${metadata["name"]}"

    fun displayName(): String {
        return "$kind ${(metadata["name"] as String)}"
    }

    fun name(): String {
        return "$kind ${(metadata["name"] as String)}"
    }

    fun resource(): K8sResource {
        val cleanSpec = spec.filterNot { it.key == "location" }
        return K8sResource(apiVersion, kind, metadata, cleanSpec as SpecType)
    }
}

data class K8sResource(
    val apiVersion: String,
    val kind: String,
    val metadata: Map<String, Any?>,
    val spec: SpecType
)
