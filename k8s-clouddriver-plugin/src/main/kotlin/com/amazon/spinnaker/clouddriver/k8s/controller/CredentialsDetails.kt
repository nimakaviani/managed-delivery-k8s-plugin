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
    private lateinit var gitRepoArtifactCredentials: List<GitRepoArtifactCredentials>
    private val logger = LoggerFactory.getLogger(CredentialsDetails::class.java)

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