package com.amazon.spinnaker.igor.k8s.config

import com.amazon.spinnaker.igor.k8s.model.GitAccount
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "git")
class PluginConfigurationProperties {
    var repositories: MutableList<GitAccount> = mutableListOf()
    var enabled: Boolean = false
}

