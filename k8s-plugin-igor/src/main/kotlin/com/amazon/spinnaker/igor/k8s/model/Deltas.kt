package com.amazon.spinnaker.igor.k8s.model

import com.netflix.spinnaker.igor.polling.PollingDelta

data class GitPollingDelta(
    val deltas: List<GitVersion>,
    val cachedIds: Set<String>
) : PollingDelta<GitVersion> {
    override fun getItems(): MutableList<GitVersion> {
        return deltas.toMutableList()
    }
}