package com.amazon.spinnaker.igor.k8s

import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.PluginConfigurationProperties
import com.amazon.spinnaker.igor.k8s.model.GitAccount
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class GitHubConfigurationTest {
    var prop: PluginConfigurationProperties = PluginConfigurationProperties()

    @BeforeEach
    fun setupMocks() {
        val repos = mutableListOf(
            GitAccount(
                name = "test1",
                type = "github",
                project = "test-project",
                url = "some-url"
            ),
            GitAccount(
                name = "test2",
                type = "github",
                project = "test-project-1",
                url = "some-url"
            ),
            GitAccount(
                name = "test3",
                type = "gitlab",
                project = "test-project-3",
                url = "some-url"
            )
        )
        prop.repositories = repos
    }

    @Test
    fun `should populate github only`() {
        val subj = GitHubAccounts(this.prop)
        expectThat(subj.accounts.size).isEqualTo(2)
        subj.accounts.forEach{
            expectThat(it.type).isEqualTo("github")
        }
    }

}