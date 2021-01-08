package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

class K8sResourceHandler (
        private val cloudDriverK8sService: CloudDriverK8sService,
        private val taskLauncher: TaskLauncher,
        private val resolvers: List<Resolver<*>>
) : ResolvableResourceHandler<K8sResourceSpec, K8sResourceTemplate>(resolvers) {

    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sResourceTemplate =
        with(resource.spec) {
            return this.template
        }

    override suspend fun current(resource: Resource<K8sResourceSpec>): K8sResourceTemplate? =
        // TODO: fix the diff between whats submitted and what is returned from k8s
        cloudDriverK8sService.getK8sResource(
            resource.spec.template,
            resource.spec.locations
        )

    private suspend fun CloudDriverK8sService.getK8sResource(
        resource: K8sResourceTemplate,
        locations: SimpleLocations
    ): K8sResourceTemplate? =
        coroutineScope {
            try {
                getK8sResource(
                    locations.account,
                    locations.account,
                    resource.namespace(),
                    resource.kindQualifiedName()
                ).toResourceModel()
            } catch (e: HttpException) {
                if (e.isNotFound) {
                    null
                } else {
                    throw e
                }
            }
        }

    private fun K8sResourceModel.toResourceModel() : K8sResourceTemplate =
        K8sResourceTemplate(
            apiVersion = manifest.apiVersion,
            kind = manifest.kind,
            metadata = manifest.metadata,
            spec = manifest.spec as K8sSpec
        )

    override suspend fun upsert(
        resource: Resource<K8sResourceSpec>,
        resourceDiff: ResourceDiff<K8sResourceTemplate>
    ): List<Task> {

        if (!resourceDiff.hasChanges()) {
            return listOf<Task>()
        }

        val spec = (resourceDiff.desired)
        val account = resource.spec.locations.account

        return listOf(
            taskLauncher.submitJob(
                resource = resource,
                description = "applying k8s resource: ${spec.name()} ",
                correlationId = spec.name(),
                job = spec.job((resource.metadata["application"] as String), account)
            )
        )
    }

    private fun K8sResourceTemplate.job(app: String, account: String): Job =
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