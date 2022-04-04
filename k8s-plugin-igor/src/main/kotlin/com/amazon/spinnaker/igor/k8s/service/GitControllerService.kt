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

package com.amazon.spinnaker.igor.k8s.service

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.model.GitVersion

class GitControllerService(
    private val gitCache: GitCache
) {

    fun getVersions(type: String, project: String, name: String): List<GitVersion> {
        return gitCache.getVersions(type, project, name).toList()
    }

    fun getVersion(type: String, project: String, name: String, version: String): GitVersion {
        return gitCache.getVersion(type, project, name, version)
    }
}