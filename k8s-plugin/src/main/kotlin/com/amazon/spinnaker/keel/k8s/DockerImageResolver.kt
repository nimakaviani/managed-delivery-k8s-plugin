package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.exception.NoDigestFound
import com.amazon.spinnaker.keel.k8s.exception.RegistryNotFound
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DockerImageResolver
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
        resource.spec.container as ContainerProvider

    override fun getAccountFromSpec(resource: Resource<K8sResourceSpec>) =
        resource.spec.deriveRegistry()

    override fun updateContainerInSpec(
        resource: Resource<K8sResourceSpec>,
        container: ContainerProvider,
        artifact: DockerArtifact,
        tag: String
    ): Resource<K8sResourceSpec> {
        val resourceTemplate = (resource.spec.template.spec["template"] as MutableMap<String, Any>)
        val updatedMap = this.setValue(resourceTemplate, "image", "${artifact.organization}/${artifact.image}:${tag}")
        resource.spec.template.spec["template"] = updatedMap
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
        if (!m.containsKey("containers")) {
            m.forEach{
                if (it.value is Map<*, *>) {
                    val childMap: MutableMap<String, Any> = (it.value as MutableMap<String, Any>)
                    val fixedMap = this.setValue(childMap, key, value)
                    m[it.key] = fixedMap
                }
            }
        } else {
            // TODO - fix setting the right reference
            val containers : ArrayList<MutableMap<String, String>> = (m["containers"] as ArrayList<MutableMap<String, String>>)
            containers.forEach{
                it[key] = value
            }
        }
        return m
    }

    protected fun K8sResourceSpec.deriveRegistry(): String =
            // TODO: fix the naming convention used for the docker registry
            "${locations.account}-registry"
//        cloudDriverCache.credentialBy("${locations.account}-registry").attributes["registry"]?.toString()
//            ?: throw RegistryNotFound(locations.account)
}


