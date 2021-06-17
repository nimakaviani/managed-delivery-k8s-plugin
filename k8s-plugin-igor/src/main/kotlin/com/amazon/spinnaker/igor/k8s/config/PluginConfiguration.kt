package com.amazon.spinnaker.igor.k8s.config

import com.amazon.spinnaker.igor.k8s.model.GitAccount
import com.amazon.spinnaker.igor.k8s.model.GitHubAccount
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "git")
class PluginConfigurationProperties {
    var accounts: MutableList<GitAccount> = mutableListOf()
    var enabled: Boolean = false
}

@Configuration
@ConditionalOnProperty("git.enabled")
@EnableConfigurationProperties(PluginConfigurationProperties::class)
open class PluginConfiguration(var pluginConfigurationProperties: PluginConfigurationProperties) {

    @Bean
    open fun gitHubAccounts(): MutableList<GitHubAccount> {
        val accounts = mutableListOf<GitHubAccount>()
        pluginConfigurationProperties.accounts.forEach{
            if (it.type.toLowerCase() == "github") {
                accounts.add(GitHubAccount(name = it.name, project = it.project))
            }
        }
        return accounts
    }
}
