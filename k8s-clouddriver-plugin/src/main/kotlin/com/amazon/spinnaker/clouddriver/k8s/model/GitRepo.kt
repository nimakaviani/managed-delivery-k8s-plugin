package com.amazon.spinnaker.clouddriver.k8s.model

data class GitRepo(
    var name: String = "",
    var token: String = "",
    var username: String = "",
    var password: String = "",
    var sshPrivateKeyFilePath: String = "",
    var sshPrivateKeyPassphrase: String = "",
    var sshPrivateKeyPassphraseCmd: String = "",
    var sshKnownHostsFilePath: String = "",
    var sshTrustUnknownHosts: Boolean = false,
    var sshPrivateKey: String = ""
)