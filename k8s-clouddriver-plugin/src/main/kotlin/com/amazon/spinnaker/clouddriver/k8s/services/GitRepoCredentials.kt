package com.amazon.spinnaker.clouddriver.k8s.services

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
                    account.token,
                    account.username,
                    account.password,
                )
                if (!account.sshPrivateKeyFilePath.isNullOrEmpty()) {
                    repo.sshPrivateKey = readSSHKey(account.sshPrivateKeyFilePath)
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
                    account.token,
                    account.username,
                    account.password,
                )
                if (!account.sshPrivateKeyFilePath.isNullOrEmpty()) {
                    out.sshPrivateKey = readSSHKey(account.sshPrivateKeyFilePath)
                }
                return out
            }
        } catch (e: MissingCredentialsException) {
            logger.info("credential with name, {}, not found", name)
            throw ResourceNotFound()
        }
    }

    // SSH Key files should be less than 16KB in size with 16384 bits. ECC keys should be much smaller.
    private fun readSSHKey(filePath: String, maxSize: Int = 16384): String {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            logger.info("key file, {}, does not exist", file)
            return ""
        }
        if (maxSize <= 0 || file.length().toInt() > maxSize) {
            logger.debug("file size is larger than maxsize of {} or max size is less than 0", maxSize)
            return ""
        }
        return file.readText()
    }
}