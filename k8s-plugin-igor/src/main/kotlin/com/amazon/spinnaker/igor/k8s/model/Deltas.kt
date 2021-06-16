package com.amazon.spinnaker.igor.k8s.model

import com.netflix.spinnaker.igor.polling.DeltaItem
import com.netflix.spinnaker.igor.polling.PollingDelta

data class GitDelta(
    val commitId: String,
    val sendEvent: Boolean = true
) : DeltaItem

data class GitPollingDelta(
    val ids: List<GitDelta>,
    val cachedIds: Set<String>
) : PollingDelta<GitDelta> {
    override fun getItems(): MutableList<GitDelta> {
        return ids.toMutableList()
    }
}