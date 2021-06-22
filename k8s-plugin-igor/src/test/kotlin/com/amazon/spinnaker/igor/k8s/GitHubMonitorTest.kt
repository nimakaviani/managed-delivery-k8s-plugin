package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.model.*
import com.amazon.spinnaker.igor.k8s.monitor.GitMonitor
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.igor.polling.PollContext
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.util.*

class GitHubMonitorTest {
    val igorProperties = mockk<IgorConfigurationProperties>()
    val gitCache = mockk<GitCache>()
    val gitHubAccounts = mockk<GitHubAccounts>()
    val gitHubRestClient = mockk<GitHubRestClient>()
    val echoService = mockk<EchoService>()
    val keelService = mockk<KeelService>()

    init {
           every {
               igorProperties.spinnaker.jedis.prefix
           } returns "igor"

            every {
                igorProperties.spinnaker.pollingSafeguard.itemUpperThreshold
            } returns 123
    }

    val gitHubMonitor = GitHubMonitor(
        igorProperties,
        DefaultRegistry(),
        null,
        null,
        Optional.empty(),
        null,
        gitCache,
        Optional.of(echoService),
        Optional.of(keelService),
        gitHubRestClient,
        gitHubAccounts
    )

    @BeforeEach
    fun setupMocks() {
        val accounts = mutableListOf(GitHubAccount(
            name = "test-name",
            project = "test-project"
        ))

        every {
            gitHubAccounts.accounts
        } returns accounts

        every {
            gitCache.getVersions("github", "test-project", "test-name")
        } returns emptySet()

        every {
            gitCache.cacheVersion(any())
        } returns Unit

        every {
            gitHubRestClient.client.getTags("test-project", "test-name")
        } returns listOf(
            GitHubTagResponse(
                "0.0.1",
                "",
                "",
                "",
                GitHubCommit("sha123", "some-url")
            )
        )

        every {
            gitHubRestClient.client.getCommit("test-project", "test-name", "sha123")
        } returns GitHubCommitResponse(
                sha = "sha123",
                htmlURL = "some_url",
                commit = Commit(
                    message = "some_message",
                    author = CommitAuthor(
                        name = "me",
                        email = "email",
                        date = "date"
                    )
                )
        )

        every {
            keelService.sendArtifactEvent(any())
        } returns null
    }

    @AfterEach
    fun removeMocks() {
        clearMocks(gitHubAccounts, gitCache, gitHubRestClient)
    }

    @Test
    fun `should cache deltas`() {
        gitHubMonitor.poll(false)

        verify {
            gitHubRestClient.client.getTags("test-project", "test-name")
            gitCache.cacheVersion(any())
            gitCache.getVersions("github", "test-project", "test-name")
            gitHubAccounts.accounts
        }
    }

    @Test
    fun `should notify keel`() {
        clearMocks(keelService)
        val slot = slot<Map<String, Any>>()
        every {
            keelService.sendArtifactEvent(capture(slot))
        } returns null

        every {
            gitCache.getVersions("github", "test-project", "test-name")
        } returns setOf("igor:git:github:test-project:test-name:0.0.1")

        every {
            gitHubRestClient.client.getTags("test-project", "test-name")
        } returns listOf(
            GitHubTagResponse(
                "0.0.2",
                "",
                "",
                "",
                GitHubCommit("sha123", "some-url")
            )
        )

        gitHubMonitor.poll(false)

        verify(exactly = 1) {
            keelService.sendArtifactEvent(any())
        }
        val payload = slot.captured["payload"] as Map<String, Object >
        val artifacts = payload["artifacts"] as List<Artifact>

        expectThat(artifacts.isNotEmpty()).isTrue()
        expectThat(artifacts[0].name).isEqualTo("test-project/test-name")
    }

    @Test
    fun `should not cache deltas`() {
        every {
            gitCache.getVersions("github", "test-project", "test-name")
        } returns setOf("igor:git:github:test-project:test-name:0.0.1")

        gitHubMonitor.poll(false)

        verify(exactly = 1) {
            gitHubRestClient.client.getTags("test-project", "test-name")
            gitCache.getVersions("github", "test-project", "test-name")
            gitHubAccounts.accounts
        }
        verify(exactly = 0) {
            gitCache.cacheVersion(any())
        }
    }

    @Test
    fun `should cache deltas when new version is available`() {
        every {
            gitCache.getVersions("github", "test-project", "test-name")
        } returns setOf("igor:git:github:test-project:test-name:0.0.1")

        every {
            gitHubRestClient.client.getTags("test-project", "test-name")
        } returns listOf(
            GitHubTagResponse(
                "0.0.1",
                "",
                "",
                "",
                GitHubCommit("sha123", "some-url")
            ),
            GitHubTagResponse(
                "0.0.2",
                "",
                "",
                "",
                GitHubCommit("sha123", "some-url")
            )
        )

        val slot = slot<GitPollingDelta>()
        every {
            gitCache.cacheVersion(capture(slot))
        } returns Unit

        gitHubMonitor.poll(false)

        verify(exactly = 1) {
            gitCache.getVersions("github", "test-project", "test-name")
            gitHubAccounts.accounts
            gitCache.cacheVersion(any())
        }
        verify {
            gitHubRestClient.client.getTags("test-project", "test-name")
        }
        expectThat(slot.captured.deltas.size).isEqualTo(1)
        expectThat(slot.captured.deltas[0].version).isEqualTo("0.0.2")
    }
}