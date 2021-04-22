package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.netflix.spinnaker.keel.api.plugins.kind

val K8S_RESOURCE_SPEC_V1  = kind <K8sResourceSpec>("k8s/resource@v1")
val HELM_RESOURCE_SPEC_V1 = kind <HelmResourceSpec>("k8s/helm@v1")

const val K8S_PROVIDER = "kubernetes"
const val SOURCE_TYPE = "text"

const val K8S_LAST_APPLIED_CONFIG: String = "kubectl.kubernetes.io/last-applied-configuration"
const val KEEL_MONIKER_APP: String = "moniker.spinnaker.io/application"

const val NAME: String = "name"
const val NAMESPACE: String = "namespace"
const val NAMESPACE_DEFAULT: String = "default"
const val ANNOTATIONS: String = "annotations"
const val LABELS: String = "labels"
const val TEMPLATE: String = "template"
const val IMAGE: String = "image"
const val CONTAINERS: String = "containers"
const val APPLICATION: String = "application"