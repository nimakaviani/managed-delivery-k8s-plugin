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