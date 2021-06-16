package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.model.GitDelta
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.amazon.spinnaker.igor.k8s.service.GitHubService
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.GitHubProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.polling.LockService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.igor.scm.AbstractScmMaster
import com.netflix.spinnaker.igor.scm.ScmMaster
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
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
                 val gitHubService: GitHubService,
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

        TODO("Not yet implemented")
    }

    override fun generateDelta(ctx: PollContext?): GitPollingDelta {
        TODO("Not yet implemented")
    }

    override fun commitDelta(delta: GitPollingDelta?, sendEvents: Boolean) {
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

}
