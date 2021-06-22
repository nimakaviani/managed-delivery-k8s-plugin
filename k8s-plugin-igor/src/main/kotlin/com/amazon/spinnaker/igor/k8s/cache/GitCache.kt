package com.amazon.spinnaker.igor.k8s.cache

import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GitCache(
    val redisClientDelegate: RedisClientDelegate,
    private val igorConfigurationProperties: IgorConfigurationProperties
) {
    private val id = "git"
    private val log = LoggerFactory.getLogger(javaClass)

    fun getVersions(type: String, project: String, name: String): Set<String> {
        val result = mutableSetOf<String>()
        redisClientDelegate.withKeyScan(
            makeIndexPattern(type, project, name),
            1000
        ) {
            result.addAll(it.results)
        }
        return result.toSet()
    }

    fun cacheVersion(gitPollingDelta: GitPollingDelta) {
        redisClientDelegate.withPipeline {
            gitPollingDelta.deltas.forEach { delta ->
                log.debug("caching $delta")
                it.hset(delta.toString(), "sha", delta.sha)
            }
            redisClientDelegate.syncPipeline(it)
        }
    }

    private fun makeIndexPattern(type: String, project: String, name: String): String {
        return "${igorConfigurationProperties.spinnaker.jedis.prefix}:$id:$type:$project:$name:*"
    }
}
