package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import java.lang.RuntimeException

typealias K8sBlob = MutableMap<String, Any?>

data class FluxCredential(
    val account: String? = null,
    val username: String? = null,
    val password: String? = null,
    val identity: String? = null,
    val known_hosts: String? = null
) {
    fun toK8sBlob(): K8sBlob {
        return jacksonObjectMapper().convertValue(this, object: TypeReference<K8sBlob>() {})
    }
}

data class Condition(
    val lastTransitionTime: String? = null,
    val message: String? = null,
    val reason: String? = null,
    val status: String? = null,
    val type: String? = null,
)

data class Status (
    val conditions: Array<Condition>?,
)

inline fun Status.isReady() : Boolean {
    this.conditions?.forEach { it ->
        if (it.type == "Ready" || it.type == "Available")
            return it.status == "True"
    }
    return true
}

open class K8sManifest(
    open var apiVersion: String?,
    open var kind: String?,
    open var metadata: MutableMap<String, Any?>,
    open var spec: K8sBlob?,
    open var data: K8sBlob? = null,
    open var status: Status? = null,
    open var items: MutableList<K8sManifest>? = null
) {
    open fun namespace(): String = (metadata[NAMESPACE] ?: NAMESPACE_DEFAULT) as String
    open fun name(): String = throw Exception("not implemented")

    // the kind qualified name is the format expected by the clouddriver
    // e.g. "pod test" would indicate a pod of name "test"
    open fun kindQualifiedName(): String = kind?.let {
        "${it.toLowerCase()} ${name()}"
    } ?: ""

    inline fun <reified R> to() : R {
        when(R::class) {
            K8sObjectManifest::class -> return K8sObjectManifest(
                apiVersion = apiVersion,
                kind = kind,
                metadata = metadata,
                spec = spec,
                data = data,
                status = status,
            ) as R

            K8sCredentialManifest::class -> return K8sCredentialManifest(
                apiVersion = apiVersion,
                kind = kind,
                metadata = metadata,
                spec = spec,
                data = data,
                status = status,
            ) as R

            else -> throw RuntimeException("not found")
        }
    }
}

data class K8sObjectManifest(
    override var apiVersion: String?,
    override var kind: String?,
    override var metadata: MutableMap<String, Any?>,
    override var spec: K8sBlob?,
    override var data: K8sBlob? = null,
    override var status: Status? = null,
    override var items: MutableList<K8sManifest>? = null
) : K8sManifest(apiVersion, kind, metadata, spec, data, status) {
    override fun name(): String = metadata[NAME] as String
}

data class K8sCredentialManifest(
    override var apiVersion: String?,
    override var kind: String?,
    override var metadata: MutableMap<String, Any?>,
    override var spec: K8sBlob?,
    override var data: K8sBlob? = null,
    override var status: Status? = null,
): K8sManifest(apiVersion, kind, metadata, spec, data, status) {
    override fun name(): String = "${data?.get(TYPE)}-${data?.get(CLOUDDRIVER_ACCOUNT)}"
}