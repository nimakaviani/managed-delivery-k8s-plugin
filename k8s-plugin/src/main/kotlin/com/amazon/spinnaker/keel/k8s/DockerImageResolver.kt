package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.exception.DuplicateReference
import com.amazon.spinnaker.keel.k8s.exception.NoDigestFound
import com.amazon.spinnaker.keel.k8s.exception.NotLinked
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DockerImageResolver
import com.netflix.spinnaker.keel.docker.MultiReferenceContainerProvider
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class DockerImageResolver(
    repository: KeelRepository,
    private val cloudDriverCache: CloudDriverCache,
    private val cloudDriverService: CloudDriverService
) : DockerImageResolver<K8sResourceSpec>(
    repository
) {
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
        val resourceTemplate = (resource.spec.template.spec[TEMPLATE] as MutableMap<String, Any>)
        val updatedMap = setValue(resourceTemplate, IMAGE, artifact.reference,
            "${artifact.organization}/${artifact.image}:${tag}")
        resource.spec.template.spec[TEMPLATE] = updatedMap
        return resource
    }

    override fun getTags(account: String, organization: String, image: String) =
        runBlocking {
            val repository = "$organization/$image"
            cloudDriverService.findDockerTagsForImage(account, repository)
        }

    override fun getDigest(account: String, organization: String, image: String, tag: String) =
        runBlocking {
            val repository = "$organization/$image"
            val images = cloudDriverService.findDockerImages(account, repository, tag)
            val img = images.firstOrNull() ?: throw NoDigestFound(repository, tag)
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
}


