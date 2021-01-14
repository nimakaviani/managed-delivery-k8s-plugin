package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Moniker

data class K8sResourceModel(
    val account: String,
    val artifacts: List<Any>?,
    val events: List<Any>?,
    val location: String?,
    val manifest: K8sObjectManifest,
    val metrics: List<Any>?,
    val moniker: Moniker?,
    val name: String?,
    val status: Map<Any, Any>?,
    val warnings: List<Any>?
)

typealias K8sSpec = MutableMap<String, Any?>

data class K8sObjectManifest(
    val apiVersion: String,
    val kind: String,
    @get:ExcludedFromDiff
    val metadata: Map<String, Any?>,
    val spec: K8sSpec
) {
    fun namespace(): String = (metadata[NAMESPACE] ?: NAMESPACE_DEFAULT) as String
    fun name(): String = metadata[NAME] as String

    // the kind qualified name is the format expected by the clouddriver
    // e.g. "pod test" would indicate a pod of name "test"
    fun kindQualifiedName(): String = "${kind} ${(metadata[NAME] as String)}"
}