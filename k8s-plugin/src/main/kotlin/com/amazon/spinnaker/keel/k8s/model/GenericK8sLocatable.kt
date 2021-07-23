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

package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.SimpleLocations

interface GenericK8sLocatable : Locatable<SimpleLocations> {
    val metadata: Map<String, String>
    val template: K8sManifest?
    val namespace: String
        get() = (template?.metadata?.get(NAMESPACE) ?: NAMESPACE_DEFAULT) as String

    override val application: String
        get() = metadata.getValue(APPLICATION).toString()

    override val id: String
        get() = "${locations.account}-$namespace-${template?.kind}-${template?.name()}".toLowerCase()

    override val displayName: String
        get() = "${locations.account}  $namespace::${template?.kind?.capitalize()}  (${template?.name()})"
}