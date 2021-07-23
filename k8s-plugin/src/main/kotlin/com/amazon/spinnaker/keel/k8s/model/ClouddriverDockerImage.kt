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

data class ClouddriverDockerImage(
    val account: String,
    val artifact: Artifact,
    val digest: String?,
    val registry: String,
    val repository: String,
    val tag: String
)

data class Artifact(
    val metadata: Metadata,
    val name: String,
    val reference: String,
    val type: String,
    val version: String
)

data class Metadata(
    val labels: Map<String, String>,
    val registry: String
)