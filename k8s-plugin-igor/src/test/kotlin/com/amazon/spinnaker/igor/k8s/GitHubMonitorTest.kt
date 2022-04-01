package com.amazon.spinnaker.igor.k8s.monitor

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.keel.KeelService
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

class GitHubMonitorTest {
    private val igorProperties = mockk<IgorConfigurationProperties>()
    private val gitCache = mockk<GitCache>()
    private val gitHubAccounts = mockk<GitHubAccounts>()
    private val gitHubRestClient = mockk<GitHubRestClient>()
    private val echoService = mockk<EchoService>()
    private val keelService = mockk<KeelService>()

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
        val accounts = mutableListOf(
            GitHubAccount(
                name = "test-name",
                project = "test-project",
                url = "some-url"
            ),
            GitHubAccount(
                name = "test-name-2",
                project = "test-project-2",
                url = "some-url-2"
            ),
        )

        every {
            gitHubAccounts.accounts
        } returns accounts

        every {
            gitCache.getVersionKeys("github", "test-project", "test-name")
        } returns emptySet()

        every {
            gitCache.getVersionKeys("github", "test-project-2", "test-name-2")
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
            gitHubRestClient.client.getTags("test-project-2", "test-name-2")
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
                html_url = "some_url",
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
            gitHubRestClient.client.getCommit("test-project-2", "test-name-2", "sha123")
        } returns GitHubCommitResponse(
            sha = "sha123",
            html_url = "some_url",
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
            gitCache.getVersionKeys("github", "test-project", "test-name")
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
            gitCache.getVersionKeys("github", "test-project", "test-name")
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
        expectThat(artifacts[0].name).isEqualTo("git-github-test-project-test-name")
    }

    @Test
    fun `should not cache deltas`() {
        every {
            gitCache.getVersionKeys("github", "test-project", "test-name")
        } returns setOf("igor:git:github:test-project:test-name:0.0.1")

        every {
            gitCache.getVersionKeys("github", "test-project-2", "test-name-2")
        } returns setOf("igor:git:github:test-project-2:test-name-2:0.0.1")

        gitHubMonitor.poll(false)

        verify {
            gitHubRestClient.client.getTags("test-project", "test-name")
            gitCache.getVersionKeys("github", "test-project", "test-name")
            gitHubAccounts.accounts
        }
        verify {
            gitHubRestClient.client.getTags("test-project-2", "test-name-2")
            gitCache.getVersionKeys("github", "test-project-2", "test-name-2")
            gitHubAccounts.accounts
        }
        verify(exactly = 0) {
            gitCache.cacheVersion(any())
        }
    }

    @Test
    fun `should cache deltas when new version is available`() {
        every {
            gitCache.getVersionKeys("github", "test-project", "test-name")
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

        val slots = mutableListOf<GitPollingDelta>()
        every {
            gitCache.cacheVersion(capture(slots))
        } returns Unit

        gitHubMonitor.poll(false)

        verify(exactly = 1) {
            gitCache.getVersionKeys("github", "test-project", "test-name")
            gitCache.getVersionKeys("github", "test-project-2", "test-name-2")
        }
        verify(exactly = 2) {
            gitCache.cacheVersion(any())
        }
        verify {
            gitHubRestClient.client.getTags("test-project", "test-name")
            gitHubRestClient.client.getTags("test-project-2", "test-name-2")
        }
        expectThat(slots.size).isEqualTo(2)
        expectThat(slots.first().deltas.size).isEqualTo(1)
        expectThat(slots.first().deltas.first().version).isEqualTo("0.0.2")

        expectThat(slots[1].deltas.size).isEqualTo(1)
        expectThat(slots[1].deltas.first().version).isEqualTo("0.0.1")
    }
}