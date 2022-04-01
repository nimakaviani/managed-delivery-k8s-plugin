package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.TaskScheduler
import java.util.*

class GitHubMonitor(
    igorProperties: IgorConfigurationProperties?,
    registry: Registry?,
    dynamicConfigService: DynamicConfigService?,
    discoveryStatusListener: DiscoveryStatusListener?,
    lockService: Optional<LockService>?,
    scheduler: TaskScheduler?,
    gitCache: GitCache,
    echoService: Optional<EchoService>,
    keelService: Optional<KeelService>,
    val gitHubRestClient: GitHubRestClient,
    val gitHubAccounts: GitHubAccounts,
) : GitMonitor(igorProperties, registry, dynamicConfigService, discoveryStatusListener,
lockService, scheduler, gitCache, echoService, keelService) {

    override fun getName(): String = "gitHubMonitor"

    override fun poll(sendEvents: Boolean) {
        log.debug("polling git accounts")
        gitHubAccounts.accounts.forEach {
            pollSingle(
                PollContext(
                    "${it.project}/${it.name}",
                    mapOf("name" to it.name, "project" to it.project, "type" to it.type, "repoUrl" to it.url),
                    !sendEvents
                )
            )
        }
    }

    override fun generateDelta(ctx: PollContext?): GitPollingDelta {
        log.debug("getting cached images. context: ${ctx?.context}")
        val deltas = mutableListOf<GitVersion>()
        val name = ctx?.context?.get("name") as String
        val project = ctx.context?.get("project") as String
        val repoUrl = ctx.context?.get("repoUrl") as String

        val cachedVersion = gitCache.getVersionKeys(ctx.context?.get("type") as String, project, name)
        log.debug("versions in cache $cachedVersion")
        val versions = getGitHubTags(name, project)
        log.debug("versions from remote: $versions")
        versions.forEach {
            if (!cachedVersion.contains(it.toString())) {
                log.debug("$it is not cached. will be cached")
                deltas.add(
                    it.copy(repoUrl = repoUrl)
                )
            }
        }
        log.info("generated ${deltas.size} deltas for $project/$name.")
        log.debug("$deltas")
        deltas.forEach {
            val commitInfo = gitHubRestClient.client.getCommit(it.project, it.name, it.commitId)
            it.url = commitInfo.html_url
            it.date = commitInfo.commit.author.date
            it.author = commitInfo.commit.author.name
            it.message = commitInfo.commit.message
            it.email = commitInfo.commit.author.email
        }

        return GitPollingDelta(
            deltas,
            cachedVersion.toSet()
        )
    }

    override fun generateMetaData(version: GitVersion): Map<String, Any> {
        return version.toMap()
    }

    private fun getGitHubTags(name: String, project: String): List<GitVersion> {
        val results = mutableListOf<GitVersion>()
        runBlocking {
            val tags = gitHubRestClient.client.getTags(project, name)
            tags.forEach {
                results.add(
                    GitVersion(
                        name,
                        project,
                        igorProperties.spinnaker.jedis.prefix,
                        it.name,
                        it.commit.sha
                    )
                )
            }
        }
        return results.toList()
    }
}