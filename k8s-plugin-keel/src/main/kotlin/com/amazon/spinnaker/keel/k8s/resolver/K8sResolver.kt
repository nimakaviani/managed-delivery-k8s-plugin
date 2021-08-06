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