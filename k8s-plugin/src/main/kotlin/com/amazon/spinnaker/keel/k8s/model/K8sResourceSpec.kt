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
import com.netflix.spinnaker.keel.api.*
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider

data class K8sResourceSpec(
    val container: ContainerProvider?,
    override val metadata: Map<String, String>,
    override val template: K8sObjectManifest,
    override val locations: SimpleLocations
) : ArtifactReferenceProvider, GenericK8sLocatable {

    override val artifactReference: String?
        get() = if (container != null) (container as ReferenceProvider).reference else null

    override val artifactType: ArtifactType?
        get() = DOCKER
}
