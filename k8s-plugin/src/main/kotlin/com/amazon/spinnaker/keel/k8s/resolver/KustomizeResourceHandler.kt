package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.MisconfiguredObjectException
import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.KustomizeResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.coroutineScope

class KustomizeResourceHandler(
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>
) : GenericK8sResourceHandler<KustomizeResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers
) {
    override val supportedKind = KUSTOMIZE_RESOURCE_SPEC_V1

    public override suspend fun toResolvedType(resource: Resource<KustomizeResourceSpec>): K8sObjectManifest {

        if (resource.spec.template.apiVersion == FLUX_KUSTOMIZE_API_VERSION && resource.spec.template.kind == FLUX_KUSTOMIZE_KIND) {
            return super.toResolvedType(resource)
        }

        // verify if passed in values are right
        try {
            require(resource.spec.template.apiVersion.isNullOrEmpty()) {"${resource.spec.template.apiVersion} doesn't match $FLUX_KUSTOMIZE_API_VERSION"}
            require(resource.spec.template.kind.isNullOrEmpty()) {"${resource.spec.template.kind} doesn't match $FLUX_KUSTOMIZE_KIND"}
        }catch(e: Exception) {
            throw MisconfiguredObjectException(e.message!!)
        }

        return K8sObjectManifest(
            FLUX_KUSTOMIZE_API_VERSION,
            FLUX_KUSTOMIZE_KIND,
            resource.spec.template.metadata,
            resource.spec.template.spec
        )
    }

    override suspend fun current(resource: Resource<KustomizeResourceSpec>): K8sObjectManifest? =
        super.current(resource)?.let {
            val lastAppliedConfig = (it.metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
            return jacksonObjectMapper().readValue<K8sObjectManifest>(lastAppliedConfig)
        }

    override suspend fun getK8sResource(r: Resource<KustomizeResourceSpec>): K8sObjectManifest? =
    // defer to GenericK8sResourceHandler to get the resource
        // from the k8s cluster
        cloudDriverK8sService.getK8sResource(r)?.let {
            it.manifest.to<K8sObjectManifest>()
        }

    override suspend fun actuationInProgress(resource: Resource<KustomizeResourceSpec>): Boolean =
        resource
            .spec.template.let {
                orcaService.getCorrelatedExecutions(it.name()).isNotEmpty()
            }
}