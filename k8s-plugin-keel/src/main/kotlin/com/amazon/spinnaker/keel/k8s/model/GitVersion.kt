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
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo

data class GitVersion(
    val name: String,
    val project: String,
    val prefix: String,
    val version: String,
    val commitId: String,
    val type: String,
    val repoUrl: String,
    var url: String? = null,
    var date: String? = null,
    var author: String? = null,
    var message: String? = null,
    var email: String? = null
) {
    fun toPublishedArtifact(reference: String): PublishedArtifact {
        return PublishedArtifact(
            "${FluxSupportedSourceType.GIT.name.toLowerCase()}-$type-$project-$name",
            FluxSupportedSourceType.GIT.name.toLowerCase(),
            reference,
            version,
            metadata = mapOf(
                "commitId" to this.commitId,
                "date" to this.date,
                "author" to this.author,
                "message" to this.message,
                "email" to this.email,
                "repoUrl" to this.repoUrl
            ),
            gitMetadata = GitMetadata(
                this.commitId,
                this.author,
                this.project,
                null,
                Repo(
                    this.name,
                    this.repoUrl
                ),
                null,
                Commit(
                    this.commitId,
                    this.url,
                    this.message
                )
            )
        )
    }
}