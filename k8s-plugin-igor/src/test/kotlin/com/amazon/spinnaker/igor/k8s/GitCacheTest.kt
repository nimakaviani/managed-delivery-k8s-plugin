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
        } returns Unit

        gitCache.getVersions("github", "project1", "repo1")
        verify(exactly = 1) {
            redisClientDelegate.withKeyScan(capture(slot), 1000, any())
        }
        expectThat(slot.captured).isEqualTo("igor:git:github:project1:repo1:*")
    }

    @Test
    fun `correct versions cached`() {
        val slot = slot<RedisPipeline>()
        every {
            redisClientDelegate.withPipeline(any())
        } returns Unit

        every {
            redisClientDelegate.syncPipeline(capture(slot))
        } returns Unit

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
        }
        slot.captured.hgetAll("igor:git:github:projec1:repo1:0.0.1")
    }
}