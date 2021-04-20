package com.amazon.spinnaker.clouddriver.k8s.services

import com.amazon.spinnaker.clouddriver.k8s.configuration.PluginConfig
import com.amazon.spinnaker.clouddriver.k8s.exceptions.ResourceNotFound
import com.amazon.spinnaker.clouddriver.k8s.model.GitRepo
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitJobExecutor
import com.netflix.spinnaker.clouddriver.artifacts.gitRepo.GitRepoArtifactCredentials
import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException
import org.slf4j.LoggerFactory
import java.io.File

class GitRepoCredentials(
    private val artifactCredentialsRepository: ArtifactCredentialsRepository,
    val pluginConfig: PluginConfig
) {

    private val executorField = GitRepoArtifactCredentials::class.java.getDeclaredField("executor")
    private val logger = LoggerFactory.getLogger(GitRepoCredentials::class.java)

    init {
        executorField.isAccessible = true
    }

    fun getAllCredentials(): List<GitRepo> {
        val creds = artifactCredentialsRepository.allCredentials
        return creds.mapNotNull {
            if (it !is GitRepoArtifactCredentials) {
                null
            } else {
                val executor = executorField.get(it) as GitJobExecutor
                val account = executor.account
                val repo = GitRepo(
                    account.name,
                    getReposForAccount(account.name),
                    account.token,
                    account.username,
                    account.password,
                )
                if (!account.sshPrivateKeyFilePath.isNullOrEmpty()) {
                    repo.sshKey = readSSHKey(account.sshPrivateKeyFilePath)
                }
                return@mapNotNull repo
            }
        }
    }

    fun getCredentials(name: String): GitRepo {
        try {
            val cred = artifactCredentialsRepository.getCredentials(name, "git/repo")
            cred.let {
                val executor = executorField.get(it) as GitJobExecutor
                val account = executor.account
                val out = GitRepo(
                    name,
                    getReposForAccount(name),
                    account.token,
                    account.username,
                    account.password,
                )
                if (!account.sshPrivateKeyFilePath.isNullOrEmpty()) {
                    out.sshKey = readSSHKey(account.sshPrivateKeyFilePath)
                }
                logger.info(pluginConfig.toString())
                return out
            }
        } catch (e: MissingCredentialsException) {
            logger.info("credential with name, {}, not found", name)
            throw ResourceNotFound()
        }
    }

    // Might need to set a maximum size
    private fun readSSHKey(filePath: String): String {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.info("key file, {}, does not exist", file)
            return ""
        }
        return file.readText()
    }

    private fun getReposForAccount(name: String): List<String> {
        pluginConfig.accounts.forEach {
            if (it.name == name) {
                return it.repos
            }
        }
        return emptyList()
    }
}