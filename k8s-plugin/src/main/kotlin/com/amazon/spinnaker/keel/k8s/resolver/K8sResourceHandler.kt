package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.*
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

class K8sResourceHandler (
    override val cloudDriverK8sService: CloudDriverK8sService,
    override val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    orcaService: OrcaService,
    override val resolvers: List<Resolver<*>>
) : GenericK8sResourceHandler<K8sResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher, eventPublisher, orcaService, resolvers
) {

    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sObjectManifest {
        // Spinnaker versions configMap and secrets by default
        if (resource.spec.template.kind == "Secret" || resource.spec.template.kind == "ConfigMap") {
            resource.spec.template.data?.let {
                val metaData = resource.spec.template.metadata
                if (metaData.containsKey("annotations")) {
                    val annotations = (metaData["annotations"] as Map<String, Any?>).toMutableMap()
                    annotations["strategy.spinnaker.io/versioned"] = "false"
                    metaData["annotations"] = annotations
                } else {
                    metaData["annotations"] = mapOf("strategy.spinnaker.io/versioned" to "false")
                }
            }
        }
        return resource.spec.template
    }

    override suspend fun current(resource: Resource<K8sResourceSpec>): K8sObjectManifest? =
        super.current(resource)?.let {
            val lastAppliedConfig = (it.metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
            return cleanup(jacksonObjectMapper().readValue(lastAppliedConfig))
        }

    override suspend fun getK8sResource(
        r: Resource<K8sResourceSpec>,
    ): K8sObjectManifest? =
        coroutineScope {
            // defer to GenericK8sResourceHandler to get the resource
            // from the k8s cluster
            val manifest = cloudDriverK8sService.getK8sResource(r)?.manifest?.to<K8sObjectManifest>()

            // notify change to the k8s vanilla image artifact based
            // on retrieved data
            manifest?.let {
                val imageString = find(it.spec ?: mutableMapOf(), IMAGE) as String?
                imageString?.let { image ->
                    log.info("Deployed artifact $image")
                    notifyArtifactDeployed(r, getTag(image))
                }
            }
            manifest
        }

    override suspend fun upsert(
        resource: Resource<K8sResourceSpec>,
        diff: ResourceDiff<K8sObjectManifest>
    ): List<Task> {
        // send a notification on attempt to deploy the image artifact
        val imageString = find(resource.spec.template.spec ?: mutableMapOf(), "image") as String?
        imageString?.let{
            log.info("Deploying artifact $it")
            notifyArtifactDeploying(resource, getTag(it))
        }
        // then defer to GenericK8sResourceHandler to deploy
        // k8s resource to the cluster
        return super.upsert(resource, diff)
    }

    private fun getTag(imageString: String): String {
        val regex = """.*:(.+)""".toRegex()
        val matchResult = regex.find(imageString)
        val (tag) = matchResult!!.destructured
        return tag
    }

    override suspend fun actuationInProgress(resource: Resource<K8sResourceSpec>): Boolean =
        resource
            .spec.template.let {
                orcaService.getCorrelatedExecutions(it.name()).isNotEmpty()
            }

}