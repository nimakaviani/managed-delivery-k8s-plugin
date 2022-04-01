package com.amazon.spinnaker.igor.k8s.cache

import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val mapper = jacksonObjectMapper()

    fun getVersionKeys(type: String, project: String, name: String): Set<String> {
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
                it.hset(delta.toString(), delta.toMap())
            }
            redisClientDelegate.syncPipeline(it)
        }
    }

    fun getVersions(type: String, project: String, name: String): Set<GitVersion> {
        val keys = getVersionKeys(type, project, name)
        val versions = mutableSetOf<GitVersion>()
        keys.forEach {
            versions.add(getVersion(it))
        }
        return versions
    }

    fun getVersion(key: String): GitVersion {
        var resultMap = emptyMap<String, String>()
        redisClientDelegate.withCommandsClient {
            resultMap = it.hgetAll(key)
        }
        return mapper.convertValue(resultMap, object: TypeReference<GitVersion>() {})
    }

    fun getVersion(type: String, project: String, name: String, version: String): GitVersion {
        return getVersion(makeVersionKey(type, project, name, version))
    }

    private fun makeIndexPattern(type: String, project: String, name: String): String {
        return "${igorConfigurationProperties.spinnaker.jedis.prefix}:$id:$type:$project:$name:*"
    }

    private fun makeVersionKey(type: String, project: String, name: String, version: String): String {
        return "${igorConfigurationProperties.spinnaker.jedis.prefix}:$id:$type:$project:$name:$version"
    }
}
