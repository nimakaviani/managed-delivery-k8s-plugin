package com.amazon.spinnaker.clouddriver.k8s

import com.amazon.spinnaker.clouddriver.k8s.configuration.PluginConfig
import com.amazon.spinnaker.clouddriver.k8s.exceptions.ResourceNotFound
import com.amazon.spinnaker.clouddriver.k8s.services.GitRepoCredentials
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitJobExecutor
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoArtifactAccount
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoArtifactCredentials
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.*
import java.io.File

@Suppress("UNCHECKED_CAST")
class GitRepoCredentialsTest {
    private val artifactCredentialsRepository = mockk<ArtifactCredentialsRepository>()
    private val gitRepoCredentials: GitRepoCredentials
    private val credentialsInRepo: MutableList<ArtifactCredentials>

    init {
        val configFile = javaClass.getResource("/testConfig.yaml")
        val resourcePath = File(configFile.file).absoluteFile.parent
        val file = configFile.readText()
        val pluginConfig = Yaml().loadAs(file, PluginConfig::class.java)
        gitRepoCredentials = GitRepoCredentials(artifactCredentialsRepository, pluginConfig)
        credentialsInRepo = pluginConfig.accounts.map {
            if (it.sshPrivateKeyFilePath.isNotEmpty()) {
                it.sshPrivateKeyFilePath = "$resourcePath/${it.sshPrivateKeyFilePath}"
            }
            GitRepoArtifactCredentials(
                GitJobExecutor(
                    GitRepoArtifactAccount(
                        it.name,
                        it.username,
                        it.password,
                        it.token,
                        it.sshPrivateKeyFilePath,
                        it.sshPrivateKeyPassphrase,
                        it.sshPrivateKeyPassphraseCmd,
                        it.sshKnownHostsFilePath,
                        it.sshTrustUnknownHosts
                    ), mockk(), "git"
                )
            )
        }.toMutableList()
    }

    @Test
    fun `correct number of accounts are returned`() {
        every { artifactCredentialsRepository.allCredentials } returns credentialsInRepo
        val results = gitRepoCredentials.getAllCredentials()
        expectThat(results).hasSize(4)
    }

    @Test
    fun `correct credentials is returned`() {
        every {
            artifactCredentialsRepository.getCredentials(
                "git-repo",
                "git/repo"
            )
        } returns credentialsInRepo.first { it.name == "git-repo" }
        val result = gitRepoCredentials.getCredentials("git-repo")
        expectThat(result.name).isEqualTo("git-repo")
        expectThat(result.token).isEqualTo("token1")
        expectThat(result.repos).isEmpty()
    }

    @Test
    fun `correct key file contents is returned`() {
        every {
            artifactCredentialsRepository.getCredentials(
                "git-repo-with-key",
                "git/repo"
            )
        } returns credentialsInRepo.first { it.name == "git-repo-with-key" }
        val result = gitRepoCredentials.getCredentials("git-repo-with-key")
        expectThat(result.sshPrivateKey) {
            isNotEmpty()
            startsWith("-----BEGIN OPENSSH PRIVATE KEY-----\n")
        }
        expectThat(result.repos).hasSize(2)
    }

    @Test
    fun `invalid key path returns nothing`() {
        every {
            artifactCredentialsRepository.getCredentials(
                "git-repo-with-invalid-key",
                "git/repo"
            )
        } returns credentialsInRepo.first { it.name == "git-repo-with-invalid-key" }
        val result = gitRepoCredentials.getCredentials("git-repo-with-invalid-key")
        expectThat(result.sshPrivateKey).isEmpty()
        expectThat(result.repos).hasSize(2)
    }

    @Test
    fun `404 is returned when credentials do not exist`() {
        every {
            artifactCredentialsRepository.getCredentials(
                "not-valid",
                "git/repo"
            )
        } throws MissingCredentialsException("doesn't exist")
        expectThrows<ResourceNotFound> { gitRepoCredentials.getCredentials("not-valid") }
    }
}
