package com.amazon.spinnaker.keel.k8s.artifactSupplier

import com.amazon.spinnaker.keel.k8s.model.GitRepoAccountDetails
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.jcraft.jsch.JSch
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.BaseArtifactSupplier
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.*
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.util.FS
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class GitArtifactSupplier (
    override val artifactMetadataService: ArtifactMetadataService,
    private val cloudDriverK8sService: CloudDriverK8sService,
    override val eventPublisher: EventPublisher
) : BaseArtifactSupplier<GitRepoArtifact, GitRepoVersionSortingStrategy>(artifactMetadataService) {

    override val supportedArtifact = SupportedArtifact("gitRepo", GitRepoArtifact::class.java)
    override val supportedSortingStrategy =
        SupportedSortingStrategy("gitRepo", GitRepoVersionSortingStrategy::class.java)
    private val pattern = Pattern.compile("^refs/tags/(.*)$")

    override fun getArtifactByVersion(artifact: DeliveryArtifact, version: String): PublishedArtifact? {
        return runBlocking {
            findArtifactVersions(artifact, version).forEach {
                if (it.version == version) {
                    return@runBlocking it
                }
            }
            null
        }
    }

    override fun getLatestArtifact(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact): PublishedArtifact? {
        return runBlocking {
            findArtifactVersions(artifact).sortedWith(artifact.sortingStrategy.comparator).firstOrNull()
        }
    }

    override fun getLatestArtifacts(
        deliveryConfig: DeliveryConfig,
        artifact: DeliveryArtifact,
        limit: Int
    ): List<PublishedArtifact> {
        return findArtifactVersions(artifact).sortedWith(artifact.sortingStrategy.comparator).take(limit)
    }

    override fun shouldProcessArtifact(artifact: PublishedArtifact): Boolean {
        return artifact.version.isNotBlank()
    }

    private fun getCredProvider(cred: GitRepoAccountDetails): UsernamePasswordCredentialsProvider? {
        return when {
            cred.token.isNotBlank() -> {
                UsernamePasswordCredentialsProvider(cred.token, "")
            }
            cred.username.isNotBlank() -> {
                UsernamePasswordCredentialsProvider(cred.username, cred.password)
            }
            else -> {
                null
            }
        }
    }

    private fun getConfigSessionFactory(cred: GitRepoAccountDetails): JschConfigSessionFactory {
        return object : JschConfigSessionFactory() {
            override fun getJSch(hc: OpenSshConfig.Host?, fs: FS?): JSch {
                val jsch = super.getJSch(hc, fs)
                jsch.removeAllIdentity()
                // Questionable adding string values like this.
                return when {
                    cred.sshPrivateKeyPassphrase.isNotBlank() && cred.sshPrivateKey.isNotBlank() -> {
                        jsch.addIdentity(cred.sshPrivateKey, cred.sshPrivateKeyPassphrase)
                        jsch
                    }
                    cred.sshPrivateKey.isNotBlank() -> {
                        jsch.addIdentity(cred.sshPrivateKey)
                        jsch
                    }
                    else -> {
                        jsch
                    }
                }
            }
        }
    }

    private fun getTransportConfigCallback(sessionFactory: JschConfigSessionFactory): TransportConfigCallback {
        return object : TransportConfigCallback {
            override fun configure(transport: Transport?) {
                if (transport is SshTransport) {
                    log.debug("setting ssh session factory")
                    transport.sshSessionFactory = sessionFactory
                }
            }
        }
    }

    private fun repositoryExists(directory: String): Boolean {
        try {
            val repo = Git.open(File(directory))
            if (repo.repository.refDatabase.findRef("HEAD") != null) {
                return true
            }
        } catch (e: IOException) {
            return false
        }
        return false
    }

    private fun populateArtifact(ref: Ref, artifact: GitRepoArtifact): PublishedArtifact {
        val matcher = pattern.matcher(ref.name)
        if (matcher.find()) {
            return PublishedArtifact(
                name = artifact.url,
                type = "gitRepo",
                reference = matcher.group(1),
                version = matcher.group(1),
                metadata = emptyMap() // TODO revwalk
            )
        }
        // bad
        return PublishedArtifact(
            name = artifact.url,
            type = "gitRepo",
            reference = "",
            version = "",
            metadata = emptyMap()
        )
    }

    private fun findArtifactVersions(
        artifact: DeliveryArtifact,
        version: String? = null
    ): List<PublishedArtifact> {
        if (artifact is GitRepoArtifact) {
            artifact.account?.let {
                runBlocking {
                    val cred = cloudDriverK8sService.getCredentialsDetails("", it)
                    val credProvider = getCredProvider(cred)
                    val repoDir = "/tmp/${artifact.url}"
                    val versions = mutableListOf<PublishedArtifact>()
                    if (repositoryExists(repoDir)) {
                        val git = Git.open(File(repoDir))
                        val fetchCmd = git.fetch()
                        when {
                            artifact.url.toLowerCase().startsWith("ssh") || artifact.url.toLowerCase()
                                .startsWith("git@") -> {
                                val sessionFactory = getConfigSessionFactory(cred)
                                val callback = getTransportConfigCallback(sessionFactory)
                                fetchCmd.setTransportConfigCallback(callback)
                            }
                            credProvider != null -> {
                                fetchCmd.setCredentialsProvider(credProvider)
                            }
                        }
                        fetchCmd.call()
                        git.tagList().call().forEach { ref ->
                            versions.add(populateArtifact(ref, artifact))
                            populateArtifact(ref, artifact)
                        }
                        git.close()
                    } else {
                        val gitCloneCmd = Git.cloneRepository()
                            .setNoCheckout(true)
                            .setDirectory(File(repoDir))
                            .setURI(artifact.url)
                        when {
                            artifact.url.toLowerCase().startsWith("ssh") || artifact.url.toLowerCase()
                                .startsWith("git@") -> {
                                val sessionfactory = getConfigSessionFactory(cred)
                                val callback = getTransportConfigCallback(sessionfactory)
                                gitCloneCmd
                                    .setTransportConfigCallback(callback)
                            }
                            credProvider != null -> {
                                gitCloneCmd
                                    .setCredentialsProvider(credProvider)
                            }
                        }
                        val git = gitCloneCmd.call()
                        git.tagList().call().forEach { ref ->
                            versions.add(populateArtifact(ref, artifact))
                            populateArtifact(ref, artifact)
                        }
                        git.close()
                    }
                    return@runBlocking versions
                }
            }
        }
        throw IllegalArgumentException("Only Git artifacts are supported by this implementation.")
    }
}