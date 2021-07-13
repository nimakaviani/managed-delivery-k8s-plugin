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