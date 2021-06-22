package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
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
                 val keelService: Optional<KeelService>
) : CommonPollingMonitor<GitVersion, GitPollingDelta>(
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
        log.debug("getting cached images. context: ${ctx?.context}")
        val deltas = mutableListOf<GitVersion>()
        val name = ctx?.context?.get("name") as String
        val project = ctx.context?.get("project") as String

        val cachedVersion = gitCache.getVersions(ctx.context?.get("type") as String, project, name)
        log.debug("versions in cache $cachedVersion")
        val versions = getGitHubTags(name, project)
        log.debug("versions from remote: $versions")
        versions.forEach {
            if (!cachedVersion.contains(it.toString())) {
                log.debug("$it is not cached. will be cached")
                deltas.add(
                    it.copy()
                )
            }
        }
        log.debug("generated ${deltas.size} deltas")
        log.trace("$deltas")
        return GitPollingDelta(
            deltas,
            cachedVersion.toSet()
        )
    }

    override fun commitDelta(delta: GitPollingDelta?, sendEvents: Boolean) {
        if (delta?.deltas?.isNotEmpty()!!) {
            log.debug("caching ${delta.deltas.size} git versions")
            delta.let {
                gitCache.cacheVersion(it)
            }
            log.debug("cached git versions")
        }
    }

//    private fun getScmMaster(type: String): AbstractScmMaster {
//        val scm: Optional<out AbstractScmMaster> = when (type) {
//            "stash" -> stashMaster
//            "github" -> gitHubMaster
//            "gitlab" -> gitLabMaster
//            "bitbucket" -> bitBucketMaster
//            else -> throw IllegalArgumentException("SCM type, ${type}, is not supported")
//        }
//        if (scm.isPresent) {
//            return scm.get()
//        } else {
//            throw IllegalArgumentException("SCM type, ${type}, is not configured")
//        }
//    }

    private fun getGitHubTags(name: String, project: String): List<GitVersion> {
        val results = mutableListOf<GitVersion>()
        runBlocking {
            val tags = gitHubRestClient.client.getTags(name, project)
            tags.forEach{
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
