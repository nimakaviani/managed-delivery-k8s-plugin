package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.K8S_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver

class K8sResolver() : Resolver<K8sResourceSpec> {
    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override fun invoke(p1: Resource<K8sResourceSpec>): Resource<K8sResourceSpec> {
        return p1
    }
}