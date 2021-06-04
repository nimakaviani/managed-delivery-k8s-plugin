package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.ResourceNotReady
import com.amazon.spinnaker.keel.k8s.model.GenericK8sLocatable
import com.amazon.spinnaker.keel.k8s.model.K8sManifest
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.model.isReady
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import retrofit2.HttpException

abstract class GenericK8sResourceHandler <S: GenericK8sLocatable, R: K8sManifest>(
    open val cloudDriverK8sService: CloudDriverK8sService,
    open val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    val orcaService: OrcaService,
    open val resolvers: List<Resolver<*>>
    ): ResolvableResourceHandler<S, R>(resolvers) {

    val logger = KotlinLogging.logger {}

    override val supportedKind: SupportedKind<S>
        get() = TODO("Not yet implemented")

    override suspend fun toResolvedType(resource: Resource<S>): R =
        with(resource.spec) {
            return (this.template as R)
        }

    open suspend fun CloudDriverK8sService.getK8sResource(
        r: Resource<S>,
    ): K8sResourceModel? =
        coroutineScope {
            try {
                r.spec.template?.let {
                    getK8sResource(
                        r.serviceAccount,
                        r.spec.locations.account,
                        it.namespace(),
                        r.spec.template!!.kindQualifiedName()
                    )
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    logger.info("resource ${r.id} not found")
                    null
                } else {
                    throw e
                }
            }
        }

    // this function needs to be implemented in child classes
    // when fetching the current resource, instead of deferring
    // to the more generic Clouddriver function
    abstract suspend fun getK8sResource(r: Resource<S>) : R?

    override suspend fun current(r: Resource<S>): R? =
        getK8sResource(r)?.let{
            log.debug("response from clouddriver for manifest: $it")
            it.status?.let {
                if (it.isReady()) {
                    log.info("${r.spec.displayName} is healthy" )
                    eventPublisher.publishEvent(ResourceHealthEvent(r, true))
                } else {
                    log.info("${r.spec.displayName} is not healthy")
                    eventPublisher.publishEvent(ResourceHealthEvent(r, false))
                    throw ResourceNotReady(r)
                }
            }
            it
        }

    override suspend fun upsert(
        resource: Resource<S>,
        diff: ResourceDiff<R>
    ): List<Task> {

        if (!diff.hasChanges()) {
            return emptyList()
        }

        val spec = (diff.desired)
        val account = resource.spec.locations.account
        log.debug("upserting. spec: $spec")
        return listOf(
            taskLauncher.submitJob(
                resource = resource,
                description = "applying k8s resource: ${spec.name()} ",
                correlationId = spec.name(),
                job = spec.job((resource.metadata["application"] as String), account)
            )
        )
    }

    fun R.job(app: String, account: String): Job =
        Job(
            "deployManifest",
            mapOf(
                "moniker" to mapOf(
                    "app" to app,
                    "location" to namespace()
                ),
                "cloudProvider" to K8S_PROVIDER,
                "credentials" to account,
                "manifests" to listOf(this),
                "optionalArtifacts" to listOf<Map<Any, Any>>(),
                "requiredArtifacts" to listOf<Map<String, Any?>>(),
                "source" to SOURCE_TYPE,
                "enableTraffic" to true.toString()
            )
        )

    fun find(m: MutableMap<String, Any?>, key: String): Any? {
        m[key]?.let{ return it }
        m.forEach{
            if (it.value is Map<*, *>) {
                val r = it.value as MutableMap<String, Any?>
                find(r, key)?.let{ nested -> return nested}
            } else if (it.value is ArrayList<*>) {
                (it.value as ArrayList<Map<*, *>>).forEach{ elem ->
                    find(elem as MutableMap<String, Any?>, key)?.let{ it -> return it }
                }
            }
        }
        return null
    }

    // remove known added labels and annotations for Keel diffing to work
    protected fun cleanup(r: R): R? {
        r.spec?.let {
            it["template"]?.let {  t ->
                (t as MutableMap<String, Any?>)?.let {
                    it["metadata"]?.let {
                        (it as MutableMap<String, MutableMap<String, Any?>>)?.let { metadata ->
                            metadata["annotations"]?.let { clean(metadata, "annotations") }
                            metadata["labels"]?.let { clean(metadata, "labels") }
                        }
                    }
                }
            }
        }

        r.metadata?.let {
            val m = it as MutableMap<String, MutableMap<String, Any?>>
            m["annotations"]?.let { clean(m, "annotations") }
            m["labels"]?.let { clean(m, "labels") }
        }

        return r
    }

    protected fun clean(r: MutableMap<String, MutableMap<String, Any?>>, attr: String) {
        arrayOf(
            "artifact.spinnaker.io/location",
            "artifact.spinnaker.io/name",
            "artifact.spinnaker.io/type",
            "artifact.spinnaker.io/version",
            "moniker.spinnaker.io/application",
            "moniker.spinnaker.io/cluster",
            "strategy.spinnaker.io/versioned",
            "app.kubernetes.io/managed-by",
            "app.kubernetes.io/name"
        ).forEach {
            r[attr]?.remove(it)
            r[attr]?.let { if (it.size == 0) r.remove(attr) }
        }
    }
}