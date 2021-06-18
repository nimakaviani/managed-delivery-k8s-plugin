package com.amazon.spinnaker.igor.k8s.cache

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.stereotype.Service

@Service
class GitCache(
    val redisClientDelegate: RedisClientDelegate,
    private val igorConfigurationProperties: IgorConfigurationProperties
) {
    private val id = "git"

    public fun getVersions(type: String, project: String, name: String): Set<String> {
        val result = mutableSetOf<String>()
        redisClientDelegate.withKeyScan(
            makeIndexPattern(type, project, name),
            1000
        ) {
            result.addAll(it.results)
        }
        return result.toSet()
    }

    public fun cacheVersion(key: String, sha: String) {
        redisClientDelegate.withCommandsClient {
            it.hset(key, "sha", sha)
        }
    }

    private fun makeIndexPattern(type: String, project: String, name: String): String {
        return "${igorConfigurationProperties.spinnaker.jedis.prefix}:$id:$type:$project:$name"
    }

}