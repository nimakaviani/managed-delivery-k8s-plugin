package com.amazon.spinnaker.igor.k8s.cache

import com.amazon.spinnaker.igor.k8s.model.GitVersion
import com.amazon.spinnaker.igor.k8s.model.GitPollingDelta
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
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
    fun `correct versions returned`() {
        val slot = slot<String>()
        every {
            redisClientDelegate.withKeyScan(capture(slot), 1000, any())
        } just Runs

        gitCache.getVersions("github", "project1", "repo1")
        verify(exactly = 1) {
            redisClientDelegate.withKeyScan(capture(slot), 1000, any())
        }
        expectThat(slot.captured).isEqualTo("igor:git:github:project1:repo1:*")
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

        gitCache.cacheVersion(
            GitPollingDelta(
            listOf(
                GitVersion(
                "repo1",
                "project1",
                "igor",
                "0.0.1",
                "123",
                ),
                GitVersion(
                    "repo1",
                    "project1",
                    "igor",
                    "0.0.2",
                    "123",
                )
            ),
            emptySet()
            )
        )

        verify(exactly = 1) {
            redisClientDelegate.withPipeline(any())
            redisClientDelegate.syncPipeline(any())
            pipeline.hset("igor:git:github:project1:repo1:0.0.1", "sha", "123")
            pipeline.hset("igor:git:github:project1:repo1:0.0.2", "sha", "123")
        }
    }
}