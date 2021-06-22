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
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.security.AuthenticatedRequest
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.util.*

abstract class GitMonitor(
    igorProperties: IgorConfigurationProperties?,
    registry: Registry?,
    dynamicConfigService: DynamicConfigService?,
    discoveryStatusListener: DiscoveryStatusListener?,
    lockService: Optional<LockService>?,
    scheduler: TaskScheduler?,
    val gitCache: GitCache,
    val echoService: Optional<EchoService>,
    val keelService: Optional<KeelService>
) : CommonPollingMonitor<GitVersion, GitPollingDelta>(
    igorProperties, registry, dynamicConfigService, discoveryStatusListener, lockService, scheduler
) {
    abstract override fun getName(): String

    abstract override fun poll(sendEvents: Boolean)

    abstract override fun generateDelta(ctx: PollContext?): GitPollingDelta

    abstract fun generateMetaData(version: GitVersion): Map<String, Any>

    override fun commitDelta(delta: GitPollingDelta?, sendEvents: Boolean) {
        if (delta?.deltas?.isNotEmpty()!!) {
            log.debug("caching ${delta.deltas.size} git versions")
            delta.let {
                gitCache.cacheVersion(it)
                log.info("cached $it")
            }
            log.debug("cached git versions")
            if (keelService.isPresent) {
                delta.deltas.forEach{
                    postEvent(delta.cachedIds, it)
                }
            }
        }
    }

    private fun postEvent(cachedVersions: Set<String>, gitVersion: GitVersion) {
        if (!keelService.isPresent || cachedVersions.isEmpty()) {
            log.debug("keel service is not enabled or nothing is in cache. Will not send notification.")
            return
        }

        val metadata = generateMetaData(gitVersion)
        val artifact = Artifact.builder()
            .type("GIT")
            .customKind(false)
            .name(gitVersion.uniqueName)
            .version(gitVersion.version)
            .location(gitVersion.uniqueName)
            .reference(gitVersion.toString())
            .metadata(metadata)
            .provenance(gitVersion.uniqueName)
            .build()
        val artifactEvent = mapOf(
            "payload" to mapOf(
                "artifacts" to listOf(artifact),
                "details" to emptyMap<String, String>()
            ),
            "eventName" to "spinnaker_artifacts_git"
        )
        log.debug("sending artifact event to keel")
        AuthenticatedRequest.allowAnonymous {
            keelService.get().sendArtifactEvent(artifactEvent)
        }
    }
}
