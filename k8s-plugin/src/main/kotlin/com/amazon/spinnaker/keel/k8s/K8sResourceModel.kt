package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations

data class K8sResourceModel(
    val account: String,
    val artifacts: List<Any>?,
    val events: List<Any>?,
    val location: String?,
    val manifest: Manifest,
    val metrics: List<Any>?,
    val moniker: Moniker?,
    val name: String?,
    val status: Map<Any, Any>?,
    val warnings: List<Any>?
)

data class Manifest(
    val apiVersion: String,
    val kind: String,
    val metadata: Map<String, Any?>,
    val spec: Map<String, Any?>
)