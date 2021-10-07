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
import com.amazon.spinnaker.keel.k8s.exception.*
import com.amazon.spinnaker.keel.k8s.model.ClouddriverDockerImage
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.*
import com.netflix.spinnaker.keel.docker.DockerImageResolver
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class DockerImageResolver(
    repository: KeelRepository,
    private val cloudDriverCache: CloudDriverCache,
    private val cloudDriverService: CloudDriverService,
    private val cloudDriverK8sService: CloudDriverK8sService
) : DockerImageResolver<K8sResourceSpec>(
    repository
) {
    private val logger = KotlinLogging.logger {}
    override val supportedKind = K8S_RESOURCE_SPEC_V1

    override fun getContainerFromSpec(resource: Resource<K8sResourceSpec>) =
        if (resource.spec.container != null) resource.spec.container as ContainerProvider else MultiReferenceContainerProvider()

    override fun getAccountFromSpec(resource: Resource<K8sResourceSpec>) =
        resource.spec.deriveRegistry()

    override fun updateContainerInSpec(
            resource: Resource<K8sResourceSpec>,
            container: ContainerProvider,
            artifact: DockerArtifact,
            tag: String
    ): Resource<K8sResourceSpec> {
        val digestProvider = (container as DigestProvider)
        val resourceTemplate = (resource.spec.template.spec?.get(TEMPLATE) as MutableMap<String, Any>)
        val updatedMap = setValue(resourceTemplate, IMAGE, artifact.reference,
            digestProvider.image)
        resource.spec.template.spec!![TEMPLATE] = updatedMap
        return resource
    }

    override fun getTags(account: String, organization: String, image: String) =
        runBlocking {
            val repository = "$organization/$image"
            cloudDriverService.findDockerTagsForImage(account, repository)
        }

    override fun getDigest(account: String, artifact: DockerArtifact, tag: String) =
        runBlocking {
            val images = cloudDriverService.findDockerImages(account, artifact.name, tag)
            val img = images.firstOrNull() ?: throw NoDigestFound(artifact.name, tag)
            img.digest ?: ""
        }

    private fun setValue(m: MutableMap<String, Any>, key: String, reference: String, value: String) : MutableMap<*, *> {
        if (!m.containsKey(CONTAINERS)) {
            m.forEach{
                if (it.value is Map<*, *>) {
                    val childMap: MutableMap<String, Any> = (it.value as MutableMap<String, Any>)
                    val fixedMap = this.setValue(childMap, key, reference, value)
                    m[it.key] = fixedMap
                }
            }
        } else {
            val containers : ArrayList<MutableMap<String, String>> = (m[CONTAINERS] as ArrayList<MutableMap<String, String>>)
            containers.filter { it[key] == reference }.also{
                if(it.size > 1) throw DuplicateReference(reference)
                if(it.isEmpty()) throw NotLinked(reference)
                it.first()[key] = value
            }
        }
        return m
    }
    protected fun K8sResourceSpec.deriveRegistry(): String =
            // TODO: fix the naming convention used for the docker registry
            "${locations.account}-registry"

    override fun invoke(resource: Resource<K8sResourceSpec>): Resource<K8sResourceSpec> {
        val container = getContainerFromSpec(resource)
        if (container is DigestProvider || (container is MultiReferenceContainerProvider && container.references.isEmpty())) {
            return resource
        }

        val deliveryConfig = repository.deliveryConfigFor(resource.id)
        val environment = repository.environmentFor(resource.id)
        val account = getAccountFromSpec(resource)

        val containers = mutableListOf<ContainerProvider>()
        if (container is MultiReferenceContainerProvider) {
            container.references.forEach {
                containers.add(ReferenceProvider(it))
            }
        } else {
            containers.add(container)
        }

        var updatedResource = resource
        containers.forEach {
            val artifact = getArtifact(it, deliveryConfig)
            val tag: String = findTagGivenDeliveryConfig(deliveryConfig, environment, artifact)
            val dockerImage = getImage(account, artifact, tag, resource.serviceAccount)
            val newContainer = DigestProvider(
                organization = "",
                image = dockerImage.artifact.reference,
                digest = dockerImage.digest ?: ""
            )
            logger.info("resolving $artifact to ${dockerImage.artifact.reference}")
            updatedResource = updateContainerInSpec(updatedResource, newContainer, artifact, tag)
        }
        return updatedResource
    }

    fun getImage(account: String, artifact: DockerArtifact, tag: String, serviceAccount: String): ClouddriverDockerImage {
        return runBlocking {
            logger.debug("getting docker images from clouddriver. account: $account, repository: ${artifact.name}, tag: $tag")
            val images = cloudDriverK8sService.findDockerImages(
                account = account, repository = artifact.name, tag = tag, user = serviceAccount)
            logger.debug("$images")
            // older clouddriver does not support repository and tag params
            images.forEach {
                if (it.account == account && it.repository == artifact.name && it.tag == tag) {
                    logger.debug("found docker image $it")
                    return@runBlocking it
                }
            }
            throw DockerImageNotFound(account, artifact.name, tag)
        }
    }
}


