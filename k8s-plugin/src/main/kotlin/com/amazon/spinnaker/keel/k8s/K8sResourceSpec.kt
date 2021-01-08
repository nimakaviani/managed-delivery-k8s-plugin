package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider

typealias K8sSpec = MutableMap<String, Any?>

data class K8sResourceSpec(
    val container: ContainerProvider?,
    val template: K8sResourceTemplate,
    override val locations: SimpleLocations
) : ArtifactReferenceProvider, ResourceSpec, Locatable<SimpleLocations> {

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

    override val artifactReference: String
        get() = (container as ReferenceProvider).reference

    override val artifactType: ArtifactType?
        get() = DOCKER
}

data class K8sResourceTemplate(
    val apiVersion: String,
    val kind: String,
    val metadata: Map<String, Any?>,
    val spec: K8sSpec
) {
    fun namespace(): String = (metadata["namespace"] ?: "default") as String
    fun name(): String = metadata["name"] as String

    // the kind qualified name is the format expected by the clouddriver
    // e.g. "pod test" would indicate a pod of name "test"
    fun kindQualifiedName(): String = "${kind.toLowerCase()} ${(metadata["name"] as String)}"
}
