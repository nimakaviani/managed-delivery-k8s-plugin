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
    var url: String? = null,
    var date: String? = null,
    var author: String? = null,
    var message: String? = null,
    var email: String? = null
) : DeltaItem {
    private val id = "git"
    val uniqueName = "$project/$name"

    override fun toString(): String {
        return "${prefix}:$id:$type:$project:$name:$version"
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is GitHubAccount && toString() == other.toString()
    }

    fun toMap(): Map<String, String> {
        return jacksonObjectMapper().convertValue(this, object: TypeReference<Map<String, String>>() {})
    }
}
