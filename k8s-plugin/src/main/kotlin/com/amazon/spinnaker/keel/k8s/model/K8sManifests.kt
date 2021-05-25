package com.amazon.spinnaker.keel.k8s.model

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import java.lang.RuntimeException

typealias K8sSpec = MutableMap<String, Any?>

data class K8sData(
    val account: String? = null,
    val username: String? = null,
    val password: String? = null,
    val identity: String? = null
)

open class K8sManifest(
    open val apiVersion: String?,
    open val kind: String?,
    @get:ExcludedFromDiff
    open val metadata: Map<String, Any?>,
    open val spec: K8sSpec?,
    open val data: K8sData? = null
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
                data = data
            ) as R

            K8sCredentialManifest::class -> return K8sCredentialManifest(
                apiVersion = apiVersion,
                kind = kind,
                metadata = metadata,
                spec = spec,
                data = data
            ) as R

            else -> throw RuntimeException("not found")
        }
    }
}

data class K8sObjectManifest(
    override var apiVersion: String?,
    override var kind: String?,
    @get:ExcludedFromDiff
    override var metadata: Map<String, Any?>,
    override var spec: K8sSpec?,
    override var data: K8sData? = null
) : K8sManifest(apiVersion, kind, metadata, spec, data) {
    override fun name(): String = metadata[NAME] as String
}

data class K8sCredentialManifest(
    override var apiVersion: String?,
    override var kind: String?,
    @get:ExcludedFromDiff
    override var metadata: Map<String, Any?>,
    override var spec: K8sSpec?,
    override var data: K8sData? = null) : K8sManifest(apiVersion, kind, metadata, spec, data) {
    override fun name(): String = "${metadata[TYPE]}-${data?.account}"
}