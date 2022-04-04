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

package com.amazon.spinnaker.igor.k8s.controller

import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.amazon.spinnaker.igor.k8s.service.GitControllerService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.util.MimeTypeUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/git"])
@ConditionalOnProperty("artifacts.gitrepo.enabled")
class GitVersionController(
    private val gitControllerService: GitControllerService,
) {

    @GetMapping(
        path = ["/version/{type}/{project}/{slug}"],
        produces = [MimeTypeUtils.APPLICATION_JSON_VALUE]
    )
    fun getGitVersions(
        @PathVariable("type") type: String,
        @PathVariable("project") project: String,
        @PathVariable("slug") slug: String
    ): List<GitVersion> {
        return gitControllerService.getVersions(type, project, slug)
    }

    @GetMapping(
        path = ["/version/{type}/{project}/{slug}/{version}"],
        produces = [MimeTypeUtils.APPLICATION_JSON_VALUE]
    )
    fun getGitVersion(
        @PathVariable("type") type: String,
        @PathVariable("project") project: String,
        @PathVariable("slug") slug: String,
        @PathVariable("version") version: String
    ): GitVersion {
        return gitControllerService.getVersion(type, project, slug, version)
    }
}