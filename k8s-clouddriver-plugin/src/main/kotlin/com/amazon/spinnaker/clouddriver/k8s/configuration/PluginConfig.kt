package com.amazon.spinnaker.clouddriver.k8s.configuration

import com.amazon.spinnaker.clouddriver.k8s.services.GitRepoCredentials
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

data class Account(
    var name: String = "",
    var sshPrivateKeyFilePath: String = "",
    var token: String = "",
    var password: String = "",
    var username: String = "",
    var sshPrivateKeyPassphrase: String = "",
    var sshPrivateKeyPassphraseCmd: String = "",
    var sshKnownHostsFilePath: String = "",
    var sshTrustUnknownHosts: Boolean = false,
    var repos: List<String> = emptyList()
)

@ConfigurationProperties(prefix = "artifacts.gitrepo")
data class PluginConfig(
    var accounts: MutableList<Account>,
    var enabled: Boolean? = false
)

@Configuration
@ConditionalOnProperty("artifacts.gitrepo.enabled")
@EnableConfigurationProperties(PluginConfig::class)
open class PluginConfiguration(var pluginConfig: PluginConfig, var repo: ArtifactCredentialsRepository) {


    @Bean
    open fun gitRepoCredentials(): GitRepoCredentials {
        return GitRepoCredentials(repo, pluginConfig)
    }
}
