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
) : ResolvableResourceHandler<K8sResourceSpec, K8sResourceSpec>(resolvers) {

    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sResourceSpec =
        with(resource.spec) {
            return K8sResourceSpec(
                apiVersion = apiVersion,
                kind = kind,
                spec = spec,
                locations = locations,
                metadata = metadata
            )
        }

    override suspend fun current(resource: Resource<K8sResourceSpec>): K8sResourceSpec? =
        cloudDriverK8sService.getK8sResource(
            resource.spec,
            resource.spec.locations
        )

    override suspend fun desired(resource: Resource<K8sResourceSpec>): K8sResourceSpec =
        with(resource.spec) {
            K8sResourceSpec(
                apiVersion = apiVersion,
                kind = kind,
                spec = spec,
                locations = locations,
                metadata = metadata
            )
        }

    private suspend fun CloudDriverK8sService.getK8sResource(
        resource: K8sResourceSpec,
        locations: SimpleLocations
    ): K8sResourceSpec? =
        coroutineScope {
            try {
                getK8sResource(
                    locations.account,
                    locations.account,
                    resource.namespace,
                    resource.name()
                ).toResourceModel(locations)
            } catch (e: HttpException) {
                if (e.isNotFound) {
                    null
                } else {
                    throw e
                }
            }
        }

    private fun K8sResourceModel.toResourceModel(locations: SimpleLocations) =
        K8sResourceSpec(
            apiVersion = manifest.apiVersion,
            kind = manifest.kind,
            metadata = manifest.metadata,
            spec = manifest.spec as SpecType,
            locations = locations
        )

    override suspend fun upsert(
        resource: Resource<K8sResourceSpec>,
        resourceDiff: ResourceDiff<K8sResourceSpec>
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

    private fun K8sResourceSpec.job(app: String, account: String): Job =
        Job(
            "deployManifest",
            mapOf(
                "moniker" to mapOf(
                    "app" to app,
                    "location" to namespace
                ),
                "cloudProvider" to K8S_PROVIDER,
                "credentials" to account,
                "manifests" to listOf(this.resource()),
                "optionalArtifacts" to listOf<Map<Any, Any>>(),
                "requiredArtifacts" to listOf<Map<String, Any?>>(),
                "source" to SOURCE_TYPE,
                "enableTraffic" to true.toString()
            )
        )
}