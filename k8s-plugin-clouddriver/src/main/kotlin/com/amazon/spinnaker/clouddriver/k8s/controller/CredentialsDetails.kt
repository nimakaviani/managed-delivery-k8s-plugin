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

package com.amazon.spinnaker.clouddriver.k8s.controller

import com.amazon.spinnaker.clouddriver.k8s.model.GitRepo
import com.amazon.spinnaker.clouddriver.k8s.services.GitRepoCredentials
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoArtifactCredentials
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/credentialsDetails"])
@ConditionalOnProperty("artifacts.gitrepo.enabled")
class CredentialsDetails(
    private val gitRepoCredentials: GitRepoCredentials,
) {

    @GetMapping(
        path = ["/gitRepo/{name}"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun getGitRepoCreds(
        @PathVariable("name") name: String,
    ): GitRepo {
        return gitRepoCredentials.getCredentials(name)
    }

    @GetMapping(
        path = ["/gitRepo"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun getAllGitRepoCreds(): List<GitRepo> {
        return gitRepoCredentials.getAllCredentials()
    }
}