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

import com.amazon.spinnaker.keel.k8s.FluxSupportedSourceType
import com.netflix.spinnaker.keel.api.ArtifactReferenceProvider
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType

abstract class BaseFluxResourceSpec:  ArtifactReferenceProvider, GenericK8sLocatable {
    abstract val artifactSpec: ArtifactSpec?

    override val artifactReference: String
        get() = artifactSpec?.ref ?: "none-specified"

    override val artifactType: ArtifactType
        get() = FluxSupportedSourceType.GIT.name.toLowerCase()
}

// allow overriding things specified in BaseFluxArtifact per resource
data class ArtifactSpec(
    val ref: String,
    val namespace: String?,
    val interval: String?
)