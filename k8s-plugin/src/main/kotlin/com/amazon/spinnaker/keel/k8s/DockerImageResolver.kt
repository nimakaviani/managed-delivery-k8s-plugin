package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.exception.NoDigestFound
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DockerImageResolver
import com.netflix.spinnaker.keel.docker.NullableReferenceProvider
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
        if (resource.spec.container != null) resource.spec.container as ContainerProvider else NullableReferenceProvider(null)

    override fun getAccountFromSpec(resource: Resource<K8sResourceSpec>) =
        resource.spec.deriveRegistry()

    override fun updateContainerInSpec(
        resource: Resource<K8sResourceSpec>,
        container: ContainerProvider,
        artifact: DockerArtifact,
        tag: String
    ): Resource<K8sResourceSpec> {
        val resourceTemplate = (resource.spec.template.spec[TEMPLATE] as MutableMap<String, Any>)
        val updatedMap = this.setValue(resourceTemplate, IMAGE, "${artifact.organization}/${artifact.image}:${tag}")
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

    private fun setValue(m: MutableMap<String, Any>, key: String, value: String) : MutableMap<*, *> {
        if (!m.containsKey(CONTAINERS)) {
            m.forEach{
                if (it.value is Map<*, *>) {
                    val childMap: MutableMap<String, Any> = (it.value as MutableMap<String, Any>)
                    val fixedMap = this.setValue(childMap, key, value)
                    m[it.key] = fixedMap
                }
            }
        } else {
            // TODO - fix setting the right reference
            val containers : ArrayList<MutableMap<String, String>> = (m[CONTAINERS] as ArrayList<MutableMap<String, String>>)
            containers.forEach{
                it[key] = value
            }
        }
        return m
    }

    protected fun K8sResourceSpec.deriveRegistry(): String =
            // TODO: fix the naming convention used for the docker registry
            "${locations.account}-registry"
}


