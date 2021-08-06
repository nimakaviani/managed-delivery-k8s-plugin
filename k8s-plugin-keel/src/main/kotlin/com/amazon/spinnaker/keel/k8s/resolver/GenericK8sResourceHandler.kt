// Copyright 2021 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.ClouddriverProcessingError
import com.amazon.spinnaker.keel.k8s.exception.InvalidArtifact
import com.amazon.spinnaker.keel.k8s.exception.ResourceNotReady
import com.amazon.spinnaker.keel.k8s.model.*
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.ResolvableResourceHandler
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import retrofit2.HttpException
import java.util.*

abstract class GenericK8sResourceHandler <S: GenericK8sLocatable, R: K8sManifest>(
    open val cloudDriverK8sService: CloudDriverK8sService,
    open val taskLauncher: TaskLauncher,
    override val eventPublisher: EventPublisher,
    open val orcaService: OrcaService,
    open val resolvers: List<Resolver<*>>
    ): ResolvableResourceHandler<S, R>(resolvers) {

    val mapper = jacksonObjectMapper()
    val logger = KotlinLogging.logger {}
    override val supportedKind: SupportedKind<S>
        get() = TODO("Not yet implemented")

    @Suppress("UNCHECKED_CAST")
    override suspend fun toResolvedType(resource: Resource<S>): R {
        resource.spec.template!!.items?.let {
            val manifests = it.map{manifest -> labelManifest(manifest)}.toMutableList()
            resource.spec.template!!.items = manifests
            return resource.spec.template as R
        }

        with(resource.spec) {
            return labelManifest(this.template!!) as R
        }
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

    override suspend fun current(resource: Resource<S>): R? =
        getK8sResource(resource)?.let{
            log.debug("response from clouddriver for manifest: $it")
            it.status?.let {
                if (it.isReady()) {
                    log.info("${resource.spec.displayName} is healthy" )
                    eventPublisher.publishEvent(ResourceHealthEvent(resource, true))
                } else {
                    log.info("${resource.spec.displayName} is not healthy")
                    eventPublisher.publishEvent(ResourceHealthEvent(resource, false))
                    throw ResourceNotReady(resource)
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

    fun R.job(app: String, account: String): OrcaJob =
        OrcaJob(
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
            it[TEMPLATE]?.let {  t ->
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

    private fun clean(r: MutableMap<String, MutableMap<String, Any?>>, attr: String) {
        arrayOf(
            "artifact.spinnaker.io/location",
            "artifact.spinnaker.io/name",
            "artifact.spinnaker.io/type",
            "artifact.spinnaker.io/version",
            "moniker.spinnaker.io/application",
            "moniker.spinnaker.io/cluster",
            "app.kubernetes.io/managed-by",
            "app.kubernetes.io/name"
        ).forEach {
            r[attr]?.remove(it)
            r[attr]?.let { if (it.size == 0) r.remove(attr) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun augmentWithLabels(spec: MutableMap<String, Any?>, extraLabels: List<Pair<String, String>>) {
        spec[TEMPLATE]?.let { tpl ->
            val template = tpl as MutableMap<String, Any?>? ?: return@let
            template["metadata"]?.let { md ->
                val metadata = md as MutableMap<String, Any?>? ?: return@let
                var labels = mutableMapOf<String, String>()
                metadata["labels"]?.let {
                    labels = it as MutableMap<String, String>
                }
                extraLabels.forEach { pair ->
                    labels[pair.first] = pair.second
                }
                metadata["labels"] = labels
            }
            template[SPEC]?.let {
                val nested = tpl as MutableMap<String, Any?>? ?: return@let
                augmentWithLabels(spec = nested, extraLabels = extraLabels)
            }
            return@let
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun labelManifest(manifest: K8sManifest): K8sManifest {
        manifest.metadata[LABELS].let {
            val labels: MutableMap<String, String> = if (it == null) {
                mutableMapOf()
            } else {
                it as MutableMap<String, String>
            }
            MANAGED_DELIVERY_PLUGIN_LABELS.forEach { label ->
                labels[label.first] = label.second
            }
            manifest.metadata[LABELS] = labels
            manifest.spec?.let { spec ->
                augmentWithLabels(spec, MANAGED_DELIVERY_PLUGIN_LABELS)
            }
        }
        return manifest
    }
}

