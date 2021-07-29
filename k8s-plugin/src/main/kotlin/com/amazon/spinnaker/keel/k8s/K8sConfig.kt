// Copyright 2021 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.amazon.spinnaker.keel.k8s.model.KustomizeResourceSpec
import com.netflix.spinnaker.keel.api.plugins.kind

val K8S_RESOURCE_SPEC_V1  = kind <K8sResourceSpec>("k8s/resource@v1")
val HELM_RESOURCE_SPEC_V1 = kind <HelmResourceSpec>("k8s/helm@v1")
val KUSTOMIZE_RESOURCE_SPEC_V1 = kind <KustomizeResourceSpec>("k8s/kustomize@v1")
val CREDENTIALS_RESOURCE_SPEC_V1 = kind <CredentialsResourceSpec>("k8s/credential@v1")

val MANAGED_DELIVERY_PLUGIN_LABELS = listOf(Pair("md.spinnaker.io/plugin", "k8s"))
const val CLOUDDRIVER_ACCOUNT_NAME_LABEL = "account.clouddriver.spinnaker.io/name"
const val CLOUDDRIVER_ACCOUNT_TYPE_LABEL = "account.clouddriver.spinnaker.io/type"

const val K8S_PROVIDER = "kubernetes"
const val SOURCE_TYPE = "text"

const val K8S_LAST_APPLIED_CONFIG: String = "kubectl.kubernetes.io/last-applied-configuration"

const val KIND: String = "kind"
const val NAME: String = "name"
const val NAMESPACE: String = "namespace"
const val TYPE: String = "type"
const val SPEC: String = "spec"
const val CLOUDDRIVER_ACCOUNT = "account"
const val NAMESPACE_DEFAULT: String = "default"
const val ANNOTATIONS: String = "annotations"
const val LABELS: String = "labels"
const val TEMPLATE: String = "template"
const val IMAGE: String = "image"
const val CONTAINERS: String = "containers"
const val APPLICATION: String = "application"
const val SECRET: String = "Secret"
const val SECRET_API_V1: String = "v1"
const val K8S_LIST: String = "List"
const val K8S_LIST_API_V1: String = "List"
const val FLUX_SOURCE_REF: String = "sourceRef"
const val FLUX_CHART: String = "chart"
const val FLUX_HELM_API_VERSION: String = "helm.toolkit.fluxcd.io/v2beta1"
const val FLUX_HELM_KIND: String = "HelmRelease"
const val FLUX_KUSTOMIZE_API_VERSION: String = "kustomize.toolkit.fluxcd.io/v1beta1"
const val FLUX_KUSTOMIZE_KIND: String = "Kustomization"
const val FLUX_SECRETS_TOKEN_USERNAME: String = "token-user"
const val FLUX_SOURCE_API_VERSION: String = "source.toolkit.fluxcd.io/v1beta1"

enum class FluxSupportedSourceType {
    GIT { override fun fluxKind(): String = "GitRepository" },
    HELM {override fun fluxKind(): String = "HelmRepository"},
    BUCKET {override fun fluxKind(): String = "Bucket"};
    abstract fun fluxKind(): String
}

val HELM_REQUIRED_FIELDS =  listOf("interval", "chart")
val KUSTOMIZE_REQUIRED_FIELDS =  listOf("interval", "prune")
