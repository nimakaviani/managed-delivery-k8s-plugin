package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Moniker
import java.lang.RuntimeException

data class K8sResourceModel(
    val account: String,
    val artifacts: List<Any>?,
    val events: List<Any>?,
    val location: String?,
    val manifest: K8sManifest,
    val metrics: List<Any>?,
    val moniker: Moniker?,
    val name: String?,
    val status: Map<Any, Any>?,
    val warnings: List<Any>?
) {
    inline fun <reified R> toManifest() : R {
        when(R::class) {
            K8sObjectManifest::class -> return K8sObjectManifest(
                apiVersion = manifest.apiVersion,
                kind = manifest.kind,
                metadata = manifest.metadata,
                spec = manifest.spec,
                data = manifest.data
            ) as R

            K8sCredentialManifest::class -> return K8sCredentialManifest(
                apiVersion = manifest.apiVersion,
                kind = manifest.kind,
                metadata = manifest.metadata,
                spec = manifest.spec,
                data = manifest.data
            ) as R

            else -> throw RuntimeException("not found")
        }
    }
}

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
}

data class K8sObjectManifest(
    override val apiVersion: String?,
    override val kind: String?,
    @get:ExcludedFromDiff
    override val metadata: Map<String, Any?>,
    override val spec: K8sSpec?,
    override val data: K8sData? = null
) : K8sManifest(apiVersion, kind, metadata, spec, data) {
    override fun name(): String = metadata[NAME] as String
}

data class K8sCredentialManifest(
     override val apiVersion: String?,
     override val kind: String?,
     @get:ExcludedFromDiff
     override val metadata: Map<String, Any?>,
     override val spec: K8sSpec?,
     override val data: K8sData? = null) : K8sManifest(apiVersion, kind, metadata, spec, data) {
    override fun name(): String = "${metadata[TYPE]}-${data?.account}"
}
