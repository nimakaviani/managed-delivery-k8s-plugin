package com.amazon.spinnaker.igor.k8s.model

data class GitAccount(
    var name: String = "",
    var type: String = "",
    var project: String = ""
)