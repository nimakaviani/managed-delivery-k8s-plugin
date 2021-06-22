package com.amazon.spinnaker.igor.k8s.model

import com.netflix.spinnaker.igor.polling.DeltaItem

data class GitVersion (
    val name: String,
    val project: String,
    val prefix: String,
    val version: String,
    val sha: String,
    val type: String = "github",
): DeltaItem {
    private val id = "git"

    override fun toString(): String {
        return "${prefix}:$id:$type:$project:$name:$version"
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is GitHubAccount && toString() == other.toString()
    }
}
