package com.amazon.spinnaker.clouddriver.k8s.controller

import com.amazon.spinnaker.clouddriver.k8s.model.GitRepo
import org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping( path = ["/credentialsDetails"])
class CredentialsDetails {

    @GetMapping(
        path = ["/gitRepo/{name}"],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun getGitRepoCreds(
        @PathVariable("name") gitRepo: String,
    ):  GitRepo {
        return GitRepo("name", listOf("repo1"), "token")
    }
}