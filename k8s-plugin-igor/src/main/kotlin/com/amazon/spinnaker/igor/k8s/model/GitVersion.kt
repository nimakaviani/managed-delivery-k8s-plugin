// Copyright 2022 Amazon.com, Inc.
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

package com.amazon.spinnaker.igor.k8s.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.igor.polling.DeltaItem

data class GitVersion(
    val name: String,
    val project: String,
    val prefix: String,
    val version: String,
    val commitId: String,
    val type: String = "github",
    var repoUrl: String = "",
    var url: String? = null,
    var date: String? = null,
    var author: String? = null,
    var message: String? = null,
    var email: String? = null
) : DeltaItem {
    private val id = "git"
    val uniqueName = "$id-$type-$project-$name"

    override fun toString(): String {
        return "${prefix}:$id:$type:$project:$name:$version"
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is GitVersion && toString() == other.toString()
    }

    fun toMap(): Map<String, String> {
        return jacksonObjectMapper().convertValue(this, object: TypeReference<Map<String, String>>() {})
    }
}
