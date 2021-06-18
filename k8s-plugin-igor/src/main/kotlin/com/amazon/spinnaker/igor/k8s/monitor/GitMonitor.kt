package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.model.GitDelta
import com.amazon.spinnaker.igor.k8s.model.GitHubVersion
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.scm.AbstractScmMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.util.*

@Service
@ConditionalOnProperty("github.base-url")
class GitMonitor(igorProperties: IgorConfigurationProperties?,
                 registry: Registry?,
                 dynamicConfigService: DynamicConfigService?,
                 discoveryStatusListener: DiscoveryStatusListener?,
                 lockService: Optional<LockService>?,
                 scheduler: TaskScheduler?,
                 val gitCache: GitCache,
                 val gitHubRestClient: GitHubRestClient,
                 val gitHubAccounts: GitHubAccounts,
                 val echoService: Optional<EchoService>,
                 val keelService: Optional<KeelService>,
                 private val stashMaster: Optional<StashMaster>,
                 private val gitHubMaster: Optional<GitHubMaster>,
                 private val gitLabMaster: Optional<GitLabMaster>,
                 private val bitBucketMaster: Optional<BitBucketMaster>
) : CommonPollingMonitor<GitDelta, GitPollingDelta>(
    igorProperties, registry, dynamicConfigService, discoveryStatusListener, lockService, scheduler
) {
    override fun getName(): String = "gitTagMonitor"

    override fun poll(sendEvents: Boolean) {
        log.debug("polling git accounts")
        gitHubAccounts.accounts.forEach {
            pollSingle(PollContext("${it.project}/${it.name}",
                mapOf("name" to it.name, "project" to it.project, "type" to it.type),
                !sendEvents)
            )
        }
    }

    override fun generateDelta(ctx: PollContext?): GitPollingDelta {
        log.debug("getting cached images. context: $ctx")
        val name = ctx?.context?.get("name") as String
        val project = ctx.context?.get("project") as String

        val cachedVersion = gitCache.getVersions(ctx.context?.get("type") as String, project, name)
        val versions = getGitHubTags(name, project)
        versions.forEach {
            if (!cachedVersion.contains(it.toString())) {
                log.debug("${it.toString()} is not cached. will be cached")

            }
        }
        TODO("Not yet implemented")
    }

    override fun commitDelta(delta: GitPollingDelta?, sendEvents: Boolean) {
        log.info("polling GitHub accounts")
        TODO("Not yet implemented")
    }

    private fun getScmMaster(type: String): AbstractScmMaster {
        val scm: Optional<out AbstractScmMaster> = when (type) {
            "stash" -> stashMaster
            "github" -> gitHubMaster
            "gitlab" -> gitLabMaster
            "bitbucket" -> bitBucketMaster
            else -> throw IllegalArgumentException("SCM type, ${type}, is not supported")
        }
        if (scm.isPresent) {
            return scm.get()
        } else {
            throw IllegalArgumentException("SCM type, ${type}, is not configured")
        }
    }

    private fun getGitHubTags(name: String, project: String): List<GitHubVersion> {
        val results = mutableListOf<GitHubVersion>()
        runBlocking {
            val tags = gitHubRestClient.client.getTags(name, project)
            tags.forEach{
                results.add(
                    GitHubVersion(
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
