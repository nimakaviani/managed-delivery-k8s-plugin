package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.K8sObjectManifest
import com.netflix.spinnaker.keel.api.SimpleLocations

data class CredentialsResourceSpec(
    override val locations: SimpleLocations,
    override val metadata: Map<String, String>,
    override val template: K8sObjectManifest?,
    override val namespace: String,
    val name: String,
    val account: String
) : GenericK8sLocatable
