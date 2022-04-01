// Copyright 2022 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
