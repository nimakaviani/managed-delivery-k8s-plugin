package com.amazon.spinnaker.igor.k8s.cache

import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import redis.clients.jedis.commands.JedisCommands
import redis.clients.jedis.commands.RedisPipeline
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.function.Consumer

class GitCacheTest {
    val redisClientDelegate = mockk<RedisClientDelegate>()
    val igorConfigurationProperties = mockk<IgorConfigurationProperties>()
    val gitCache = GitCache(
        redisClientDelegate,
        igorConfigurationProperties
    )

    init {
        every {
            igorConfigurationProperties.spinnaker.jedis.prefix
        } returns "igor"

        every {
            igorConfigurationProperties.spinnaker.pollingSafeguard.itemUpperThreshold
        } returns 123
    }

    @AfterEach
    fun removeMocks() {
        clearMocks(redisClientDelegate)
    }

    @Test
    fun `version keys returned`() {
        val slot = slot<String>()
        every {
            redisClientDelegate.withKeyScan(capture(slot), 1000, any())
        } just Runs

        gitCache.getVersionKeys("github", "project1", "repo1")
        verify(exactly = 1) {
            redisClientDelegate.withKeyScan(capture(slot), 1000, any())
        }
        expectThat(slot.captured).isEqualTo("igor:git:github:project1:repo1:*")
    }

    @Test
    fun `cached versions returned`() {
        val jedisCommands = mockk<JedisCommands>()
        val cachedVersion = mutableMapOf(
            "name" to "test-repo",
            "project" to "test-project",
            "prefix" to "igor",
            "version" to "1.0.0",
            "commitId" to "sha123",
            "type" to "github",
            "repoUrl" to "some-url",
            "url" to "some-url",
            "date" to "some-date",
            "author" to "author1",
            "message" to "message1",
            "email" to "email1"
        )
        every {
            jedisCommands.hgetAll("igor:git:github:test-project:test-repo:1.0.0")
        } returns cachedVersion

        every {
            redisClientDelegate.withCommandsClient(any())
        } answers {
            val consumer = firstArg<(Consumer<JedisCommands>)>()
            consumer.accept(jedisCommands)
        }

        val result =  gitCache.getVersion("github", "test-project", "test-repo", "1.0.0")
        val returnedMap = result.toMap().toMutableMap()
        returnedMap.remove("uniqueName")

        expectThat(result.uniqueName).isEqualTo("git/github/test-project/test-repo")
        expectThat(returnedMap).isEqualTo(cachedVersion)
    }

    @Test
    fun `correct versions cached`() {
        val pipeline = mockk<RedisPipeline>(relaxed = true)

        every {
            redisClientDelegate.withPipeline(any())
        } answers {
            val consumer = firstArg<(Consumer<RedisPipeline>)>()
            consumer.accept(pipeline)
        }

        every {
            redisClientDelegate.syncPipeline(pipeline)
        } just Runs

        val firstVersion = GitVersion(
            "repo1",
            "project1",
            "igor",
            "0.0.1",
            "123",
        )
        val secondVersion = GitVersion(
            "repo1",
            "project1",
            "igor",
            "0.0.2",
            "123",
        )

        gitCache.cacheVersion(
            GitPollingDelta(
            listOf(firstVersion, secondVersion),
            emptySet()
            )
        )

        verify(exactly = 1) {
            redisClientDelegate.withPipeline(any())
            redisClientDelegate.syncPipeline(any())
            pipeline.hset("igor:git:github:project1:repo1:0.0.1", firstVersion.toMap())
            pipeline.hset("igor:git:github:project1:repo1:0.0.2", secondVersion.toMap())
        }
    }
}