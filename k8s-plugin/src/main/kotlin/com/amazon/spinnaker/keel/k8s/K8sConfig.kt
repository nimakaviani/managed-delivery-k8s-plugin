package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.plugins.kind

val K8S_RESOURCE_SPEC_V1 = kind <K8sResourceSpec>("k8s/resource@v1")
const val K8S_PROVIDER = "kubernetes"
const val SOURCE_TYPE = "text"