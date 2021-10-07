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
import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitRepoVersionSortingStrategy
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.artifacts.*

data class GitRepoArtifact(
    override var deliveryConfigName: String? = null,
    override val reference: String,
    val repoName: String,
    val project: String,
    val gitType: String,
    val tagVersionStrategy: TagVersionStrategy,
    override val namespace: String = "flux-system",
    override val interval: String = "1m",
    override val secretRef: String? = null,
) : BaseFluxArtifact() {
    override val type = FluxSupportedSourceType.GIT.name.toLowerCase()
    override val name = "$type-$gitType-$project-$repoName"
    override val kind = FluxSupportedSourceType.GIT.fluxKind()

    @JsonIgnore
    override val statuses: Set<ArtifactStatus> = emptySet()

    override val sortingStrategy: SortingStrategy = GitRepoVersionSortingStrategy(tagVersionStrategy)

    override fun withDeliveryConfigName(deliveryConfigName: String): DeliveryArtifact {
        return this.copy(deliveryConfigName = deliveryConfigName)
    }
}
