package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.model.K8sManifest
import com.netflix.spinnaker.keel.api.Moniker

data class K8sResourceModel(
    val account: String,
    val artifacts: List<Any>?,
    val events: List<Any>?,
    val location: String?,
    val manifest: K8sManifest,
    val metrics: List<Any>?,
    val moniker: Moniker?,
    val name: String?,
    val status: Map<Any, Any>?,
    val warnings: List<Any>?
)
