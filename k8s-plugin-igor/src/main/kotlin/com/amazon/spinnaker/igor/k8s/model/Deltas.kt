package com.amazon.spinnaker.igor.k8s.model

import com.netflix.spinnaker.igor.polling.PollingDelta

data class GitPollingDelta(
    val deltas: List<GitHubVersion>,
    val cachedIds: Set<String>
) : PollingDelta<GitHubVersion> {
    override fun getItems(): MutableList<GitHubVersion> {
        return deltas.toMutableList()
    }
}