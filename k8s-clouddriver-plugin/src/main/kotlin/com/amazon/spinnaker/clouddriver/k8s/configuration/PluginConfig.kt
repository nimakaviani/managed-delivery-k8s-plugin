package com.amazon.spinnaker.clouddriver.k8s.configuration

import com.amazon.spinnaker.clouddriver.k8s.model.GitRepo
import com.amazon.spinnaker.clouddriver.k8s.services.GitRepoCredentials
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@ConfigurationProperties(prefix = "artifacts.gitrepo")
data class PluginConfig(
    var accounts: MutableList<GitRepo> = mutableListOf(),
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
