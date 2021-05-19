package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
import com.amazon.spinnaker.keel.k8s.model.GenericK8sLocatable
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.support.EventPublisher
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
}