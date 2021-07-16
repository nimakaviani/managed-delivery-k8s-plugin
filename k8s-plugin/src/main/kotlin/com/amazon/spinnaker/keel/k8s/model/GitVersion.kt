package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.GIT
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
            "$GIT-$type-$project-$name",
            GIT,
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