package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.coroutineScope

class HelmResourceHandler(
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>
) : GenericK8sResourceHandler<HelmResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers
) {
    override val supportedKind = HELM_RESOURCE_SPEC_V1

    override suspend fun current(resource: Resource<HelmResourceSpec>): K8sObjectManifest? {
        val clusterResource = cloudDriverK8sService.getK8sResource(resource) ?: return null
        val mapper = jacksonObjectMapper()
        val lastAppliedConfig = (clusterResource.metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
        return mapper.readValue<K8sObjectManifest>(lastAppliedConfig)
    }

    override suspend fun getK8sResource(r: Resource<HelmResourceSpec>): K8sObjectManifest? =
        coroutineScope {
            // defer to GenericK8sResourceHandler to get the resource
            // from the k8s cluster
            cloudDriverK8sService.getK8sResource(r) ?: null
        }
}