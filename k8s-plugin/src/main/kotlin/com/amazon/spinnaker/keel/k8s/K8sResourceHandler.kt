package com.amazon.spinnaker.keel.k8s

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import kotlin.collections.ArrayList

const val LAST_APPLIED_CONFIG: String = "kubectl.kubernetes.io/last-applied-configuration"

class K8sResourceHandler (
    private val cloudDriverK8sService: CloudDriverK8sService,
    private val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    private val resolvers: List<Resolver<*>>
) : ResolvableResourceHandler<K8sResourceSpec, K8sObjectManifest>(resolvers) {

    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sObjectManifest =
        with(resource.spec) {
            return this.template
        }

    override suspend fun current(resource: Resource<K8sResourceSpec>): K8sObjectManifest? {
        val clusterResource = cloudDriverK8sService.getK8sResource(resource) ?: return null
        val mapper = jacksonObjectMapper()
        val lastAppliedConfig = (clusterResource.metadata["annotations"] as Map<String, String>)[LAST_APPLIED_CONFIG] as String
        return sanitize(mapper.readValue<K8sObjectManifest>(lastAppliedConfig))
    }

    private suspend fun CloudDriverK8sService.getK8sResource(
        r: Resource<K8sResourceSpec>
    ): K8sObjectManifest? =
        coroutineScope {
            try {
                val manifest = getK8sResource(
                    r.spec.locations.account,
                    r.spec.locations.account,
                    r.spec.template.namespace(),
                    r.spec.template.kindQualifiedName()
                ).toResourceModel()

                val imageString = find(manifest.spec, "image") as String
                getTag(imageString).let {
                    log.info("Deployed artifact $imageString")
                    notifyArtifactDeployed(r, it)
                }

                manifest
            } catch (e: HttpException) {
                if (e.isNotFound) {
                    null
                } else {
                    throw e
                }
            }
        }

    private fun K8sResourceModel.toResourceModel() : K8sObjectManifest =
        K8sObjectManifest(
            apiVersion = manifest.apiVersion,
            kind = manifest.kind,
            metadata = manifest.metadata,
            spec = manifest.spec
        )

    override suspend fun upsert(
        resource: Resource<K8sResourceSpec>,
        diff: ResourceDiff<K8sObjectManifest>
    ): List<Task> {

        if (!diff.hasChanges()) {
            return emptyList()
        }

        val imageString = find(resource.spec.template.spec as MutableMap<String, Any?>, "image") as String
        if (!diff.hasChanges()) {
            return emptyList()
        }

        log.info("Deploying artifact $imageString")
        notifyArtifactDeploying(resource, getTag(imageString))
        val spec = (diff.desired)
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

    private fun K8sObjectManifest.job(app: String, account: String): Job =
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

    private fun sanitize(r: K8sObjectManifest): K8sObjectManifest {
        val fluff = arrayOf(
            "app.kubernetes.io/managed-by",
            "app.kubernetes.io/name",
            "artifact.spinnaker.io/location",
            "artifact.spinnaker.io/name",
            "artifact.spinnaker.io/type",
            "artifact.spinnaker.io/version",
            "moniker.spinnaker.io/cluster"
        )

        val labels = r.metadata["labels"] as MutableMap<String, Any>
        val annotations = r.metadata["annotations"] as MutableMap<String, Any>
        val template = r.spec["template"] as MutableMap<String, Any>

        clean(labels, fluff)
        clean(annotations, fluff)
        clean(template, fluff)

        if (labels.isEmpty()) (r.metadata as MutableMap<String, Any>).remove("labels")
        if (annotations.isEmpty()) (r.metadata as MutableMap<String, Any>).remove("labels")
        return r
    }

    private fun clean(m: MutableMap<String, Any>, keys: Array<String>) : MutableMap<*, *> {
        keys.forEach{ key -> m.remove(key)}
        m.forEach{
            if (it.value is Map<*, *>) {
                val r = it.value as MutableMap<String, Any>
                clean(r, keys)
            }
        }

        return m
    }

    private fun getTag(imageString: String): String {
        val regex = """.*:(.+)""".toRegex()
        val matchResult = regex.find(imageString)
        val (tag) = matchResult!!.destructured
        return tag
    }

    private fun find(m: MutableMap<String, Any?>, key: String): Any? {
        if(m.containsKey(key)) return m[key]
        m.forEach{
            if (it.value is Map<*, *>) {
                val r = it.value as MutableMap<String, Any>
                val value = find(r as MutableMap<String, Any?>, key)
                if (value != null) return value
            } else if (it.value is ArrayList<*>) {
                (it.value as ArrayList<Map<*, *>>).forEach{
                    val value = find(it as MutableMap<String, Any?>, key)
                    if (value != null) return value
                }
            }
        }
        return null
    }
}