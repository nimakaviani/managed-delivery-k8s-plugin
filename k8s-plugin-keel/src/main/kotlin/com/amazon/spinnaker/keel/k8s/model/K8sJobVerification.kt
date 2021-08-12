package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.VERIFICATION_K8S_JOB
import com.netflix.spinnaker.keel.api.Verification
import java.security.MessageDigest

data class K8sJobVerification(
    val account: String,
    val manifest: Map<String, Any>
) : Verification {

    override val type = VERIFICATION_K8S_JOB
    // hashcode will be different if field values are different.
    override val id by lazy {
        "$account/${Integer.toHexString(hashCode())}"
    }
}