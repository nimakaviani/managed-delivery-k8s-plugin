package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.ClouddriverProcessingError
import com.amazon.spinnaker.keel.k8s.exception.InvalidArtifact
import com.amazon.spinnaker.keel.k8s.model.*
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

abstract class BaseFluxHandler<S : BaseFluxResourceSpec, R : K8sManifest>(
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    override val orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>,
    open val repository: KeelRepository
) : GenericK8sResourceHandler<S, R>(cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers) {

    abstract fun generateCorrelationId(resource: Resource<S>): String

    override suspend fun current(resource: Resource<S>): R? {
        val deployed = getK8sResource(resource)
        // Check health of resources returned by clouddriver
        if (deployed != null) {
            notifyHealthAndArtifactDeployment(deployed, resource)
        }
        return deployed
    }


    override suspend fun getK8sResource(r: Resource<S>): R? {
        val environment = repository.environmentFor(r.id)
        val (artifact, _) = getArtifactAndConfig(r)
        when (artifact) {
            is GitRepoArtifact -> {
                val resolvedArtifact = resolveArtifactSpec(r, artifact)
                return getFluxK8sResources(r, resolvedArtifact, generateCorrelationId(r), environment.name)
            }
            else -> {
                throw InvalidArtifact("artifact is not a supported artifact type. artifact: $artifact")
            }
        }
    }

    override suspend fun actuationInProgress(resource: Resource<S>): Boolean =
        resource.spec.template.let {
            log.debug("Checking if actuation is in progress")
            orcaService.getCorrelatedExecutions(generateCorrelationId(resource)).isNotEmpty()
        }

    override suspend fun upsert(
        resource: Resource<S>,
        diff: ResourceDiff<R>
    ): List<Task> {
        val spec = (diff.desired)
        spec.items?.forEach { manifest ->
            when (manifest.kind) {
                FluxSupportedSourceType.GIT.fluxKind() -> {
                    findAndNotifyGitArtifactDeployment(manifest, resource, "tag")
                }
                //TODO add support for other artifact types
            }
        } ?: log.warn("generated resource does not have anything under items field. Please check your configuration")
        return super.upsert(resource, diff)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getFluxK8sResources(
        resource: Resource<S>,
        artifact: BaseFluxArtifact,
        correlationId: String,
        environment: String?
    ): R? {
        val namespace = getNamespace(resource)
        val repoNamespace: String = artifact.namespace
        val repoNameInClouddriver: String = environment?.let {
            "${artifact.kind} ${artifact.name}-$it"
        } ?: run { "${artifact.kind} ${artifact.name}" }

        // need to wrap async with coroutineScope to catch exceptions correctly
        try {
            return coroutineScope {
                val repoJob = async {
                    cloudDriverK8sService.getK8sResource(
                        resource.serviceAccount,
                        resource.spec.locations.account,
                        repoNamespace,
                        repoNameInClouddriver
                    )
                }
                val resourceJob = async {
                    cloudDriverK8sService.getK8sResource(
                        resource.serviceAccount,
                        resource.spec.locations.account,
                        namespace,
                        resource.spec.template!!.kindQualifiedName()
                    )
                }
                val repoResponse = repoJob.await()
                val resourceResponse = resourceJob.await()

                val lastAppliedConfigRepo =
                    (repoResponse.manifest.to<K8sObjectManifest>().metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
                val repoManifest = cleanup(mapper.readValue<K8sObjectManifest>(lastAppliedConfigRepo) as R)

                val lastAppliedConfigResource =
                    (resourceResponse.manifest.to<K8sObjectManifest>().metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
                val resourceManifest = cleanup(mapper.readValue<K8sObjectManifest>(lastAppliedConfigResource) as R)

                if (repoManifest == null || resourceManifest == null) {
                    log.error("unable to read last applied config from clouddriver.")
                    throw ClouddriverProcessingError("unable to read last applied config from clouddriver.")
                }
                return@coroutineScope toK8sList(repoManifest, resourceManifest, correlationId)
            }
        } catch (e: HttpException) {
            if (e.code() == 404) {
                log.info("resource ${resource.id} not found")
                return null
            } else {
                throw e
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getNamespace(resource: Resource<S>): String {
        val metadata = resource.spec.template!!.metadata as Map<String, String>?
        return metadata?.let {
            val ns = it[NAMESPACE]
            if (ns.isNullOrEmpty() || ns.isBlank()) {
                return@let NAMESPACE_DEFAULT
            }
            ns
        } ?: NAMESPACE_DEFAULT
    }

    @Suppress("UNCHECKED_CAST")
    fun toK8sList(
        repoManifest: R,
        resourceManifest: R,
        name: String
    ): R {
        return K8sObjectManifest(
            K8S_LIST_API_V1,
            K8S_LIST,
            mutableMapOf(
                "name" to name
            ),
            null,
            null,
            null,
            mutableListOf(
                repoManifest,
                resourceManifest
            )
        ) as R
    }

    private fun notifyHealthAndArtifactDeployment(manifest: R, resource: Resource<S>) {
        manifest.let outer@{
            log.debug("response from clouddriver for manifest: $it")
            it.items?.forEach { manifest ->
                manifest.status?.let inner@{ status ->
                    if (!status.isReady()) {
                        log.info("${manifest.kind}-${manifest.name()} is NOT healthy")
                        eventPublisher.publishEvent(ResourceHealthEvent(resource, false))
                        return@outer
                    }
                    log.info("${manifest.kind}-${manifest.name()} is healthy")
                }
                if (manifest.kind == FluxSupportedSourceType.GIT.fluxKind()) {
                    val tag = find(manifest.spec ?: mutableMapOf(), "tag") as String?
                    tag?.let { t ->
                        log.info("Deployed Git artifact $t")
                        notifyArtifactDeployed(resource, t)
                    }
                }
            } ?: return@outer
            log.debug("notifying resource is healthy")
            eventPublisher.publishEvent(ResourceHealthEvent(resource, true))
        }
    }

    fun getArtifactAndConfig(resource: Resource<S>): Pair<BaseFluxArtifact, DeliveryConfig> {
        val deliveryConfig = repository.deliveryConfigFor(resource.id)
        resource.spec.artifactReference.let { artifactRef ->
            val artifact = deliveryConfig.artifacts.find {
                log.trace("checking $it")
                it.reference == artifactRef
            } ?: throw NoMatchingArtifactException(
                deliveryConfigName = deliveryConfig.name,
                type = "unknown",
                reference = artifactRef
            )

            log.debug("found FluxArtifact: $artifact")
            return Pair(artifact as BaseFluxArtifact, deliveryConfig)
        }
    }

    inline fun <reified T : BaseFluxArtifact> resolveArtifactSpec(resource: Resource<S>, artifact: T): T {
        when (artifact) {
            is GitRepoArtifact -> {
                return artifact.copy(
                    namespace = resource.spec.artifactSpec.namespace ?: artifact.namespace,
                    interval = resource.spec.artifactSpec.interval ?: artifact.interval
                ) as T
            }
            // TODO add support for more artifacts
            else -> {
                throw InvalidArtifact("artifact is not a supported artifact type. artifact: $artifact")
            }
        }
    }

    private fun findAndNotifyGitArtifactDeployment(
        manifest: K8sManifest,
        resource: Resource<S>,
        versionString: String
    ) {
        val version = find(manifest.spec ?: mutableMapOf(), versionString) as String?
        log.debug("found version: $version")
        version?.let {
            log.info("Deploying Git artifact $it")
            notifyArtifactDeploying(resource, it)
        }
    }
}
