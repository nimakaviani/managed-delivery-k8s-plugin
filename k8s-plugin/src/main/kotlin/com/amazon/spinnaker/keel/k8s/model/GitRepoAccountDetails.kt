package com.amazon.spinnaker.keel.k8s.model

data class GitRepoAccountDetails(
    val name: String = "",
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val sshPrivateKeyFilePath: String = "",
    val sshPrivateKeyPassphrase: String = "",
    val sshPrivateKeyPassphraseCmd: String = "",
    val sshKnownHostsFilePath: String = "",
    val sshTrustUnknownHosts: Boolean = false,
    val repos: List<String> = emptyList(),
    val sshPrivateKey: String = ""
)