package com.amazon.spinnaker.keel.tests;

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.DuplicateReference
import com.amazon.spinnaker.keel.k8s.exception.NotLinked
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.amazon.spinnaker.keel.k8s.resolver.DockerImageResolver
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.persistence.KeelRepository;
import com.netflix.spinnaker.keel.artifacts.DockerArtifact;
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.docker.MultiReferenceContainerProvider
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

@Suppress("UNCHECKED_CAST")
internal class DockerImageResolverTest : JUnit5Minutests {
    val repository: KeelRepository = mockk()
    val clouddriverCache : CloudDriverCache = mockk()
    val clouddriverService : CloudDriverService = mockk()

    private val artifacts = setOf(
        DockerArtifact(name = "spkr/main", reference = "main-container", tagVersionStrategy = SEMVER_TAG, deliveryConfigName = "mydeliveryconfig"),
        DockerArtifact(name = "spkr/sidecar", reference = "sidecar-container", tagVersionStrategy = SEMVER_TAG, deliveryConfigName = "mydeliveryconfig")
    )

    fun deploymentSpec(references: Set<String>, containers: List<Map<String, String>>) = Resource(
        kind = K8S_RESOURCE_SPEC_V1.kind,
        metadata = mapOf(
            "id" to "deployment",
            "application" to "test"
        ),
        spec = K8sResourceSpec(
            container = MultiReferenceContainerProvider(
                references = references
            ),
            locations = SimpleLocations(
                account = "test",
                regions = emptySet()
            ),
            metadata = mapOf("application" to "test"),
            template = K8sObjectManifest(
                apiVersion = "apps/v1",
                kind = "Deployment",
                metadata = mapOf(
                    "name" to "hello-kubernetes",
                    "namespace" to NAMESPACE_DEFAULT
                ),
                spec = mutableMapOf<String, Any>(
                        "template" to mutableMapOf(
                            "spec" to mutableMapOf(
                                "containers" to containers
                            )
                        )
                    ) as K8sSpec
            ),
        )
    )

    fun serviceSpec() = Resource(
        kind = K8S_RESOURCE_SPEC_V1.kind,
        metadata = mapOf(
            "id" to "service",
            "application" to "test"
        ),
        spec = K8sResourceSpec(
            container = null,
            locations = SimpleLocations(
                account = "test",
                regions = emptySet()
            ),
            metadata = mapOf("application" to "test"),
            template = K8sObjectManifest(
                apiVersion = "v1",
                kind = "Service",
                metadata = mapOf(
                    "name" to "hello-svc",
                    "namespace" to NAMESPACE_DEFAULT
                ),
                spec = mutableMapOf<String, Any>(
                    "template" to emptyMap<String, Any>()
                ) as K8sSpec
            )
        )
    )

    fun deliveryConfig(resources: Set<Resource<K8sResourceSpec>> ) = DeliveryConfig(
        name = "mydeliveryconfig",
        application = "keel",
        serviceAccount = "keel@spinnaker",
        artifacts = artifacts,
        environments = setOf(
            Environment(
                name = "test",
                resources = resources
            )
        )
    )

    fun tests() = rootContext<DockerImageResolver> {
        fixture {
            DockerImageResolver(repository, clouddriverCache, clouddriverService)
        }

        lateinit var deploymentSpec : Resource<K8sResourceSpec>
        lateinit var deliveryConfig : DeliveryConfig

        context("for k8s delivery config manifests with approved artifacts") {
            before {
                coEvery { clouddriverService.findDockerImages("test-registry", "spkr/main", "0.0.1", null) } returns
                        listOf(DockerImage("test-registry", "spkr/main", "0.0.1", "sha:1111"))
                coEvery { clouddriverService.findDockerImages("test-registry", "spkr/sidecar", "0.0.2", null) } returns
                        listOf(DockerImage("test-registry", "spkr/sidecar", "0.0.2", "sha:2222"))

            }


            context("for a k8s resource with a single container") {
                before {
                    deploymentSpec = deploymentSpec(
                        references = mutableSetOf("main-container"),
                        containers = arrayListOf(
                            mapOf(
                                "name" to "main",
                                "image" to "main-container"

                            )
                        )
                    )
                    deliveryConfig = deliveryConfig(resources = setOf(deploymentSpec))
                    every { repository.deliveryConfigFor(deploymentSpec.id) } returns deliveryConfig
                    every { repository.environmentFor(deploymentSpec.id) } returns deliveryConfig.environments.first()

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(0),
                            "test"
                        )
                    } returns "0.0.1"

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(1),
                            "test"
                        )
                    } returns "0.0.2"
                }

                test("the main contianer is resolved correctly") {
                    val resolvedResource = this.invoke(deploymentSpec)
                    expect {
                        that(getImageWithName(resolvedResource, "main")).isEqualTo("spkr/main:0.0.1")
                    }
                }
            }

            context("for a k8s resource with multiple containers") {
                before {
                    deploymentSpec = deploymentSpec(
                        references = mutableSetOf("main-container", "sidecar-container"),
                        containers = arrayListOf(
                            mapOf(
                                "name" to "main",
                                "image" to "main-container"
                            ),
                            mapOf(
                                "name" to "sidecar",
                                "image" to "sidecar-container"
                            )
                        )
                    )

                    deliveryConfig = deliveryConfig(resources = setOf(deploymentSpec))

                    deliveryConfig = deliveryConfig(resources = setOf(deploymentSpec))
                    every { repository.deliveryConfigFor(deploymentSpec.id) } returns deliveryConfig
                    every { repository.environmentFor(deploymentSpec.id) } returns deliveryConfig.environments.first()

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(0),
                            "test"
                        )
                    } returns "0.0.1"

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(1),
                            "test"
                        )
                    } returns "0.0.2"
                }

                test("both containers are resolved correctly") {
                    val resolvedResource = this.invoke(deploymentSpec)
                    expect {
                        that(getImageWithName(resolvedResource, "main")).isEqualTo("spkr/main:0.0.1")
                    }
                    expect {
                        that(getImageWithName(resolvedResource, "sidecar")).isEqualTo("spkr/sidecar:0.0.2")
                    }
                }
            }

            context("for a k8s resource with missing reference") {
                before {
                    deploymentSpec = deploymentSpec(
                        references = mutableSetOf("main-container", "sidecar-container"),
                        containers = arrayListOf(
                            mapOf(
                                "name" to "main",
                                "image" to "main-container"
                            )
                        )
                    )

                    deliveryConfig = deliveryConfig(resources = setOf(deploymentSpec))

                    every { repository.deliveryConfigFor(deploymentSpec.id) } returns deliveryConfig
                    every { repository.environmentFor(deploymentSpec.id) } returns deliveryConfig.environments.first()

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(0),
                            "test"
                        )
                    } returns "0.0.1"

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(1),
                            "test"
                        )
                    } returns "0.0.2"
                }

                test("a NotLinked exception is thrown") {
                    expectThrows<NotLinked> { this.invoke(deploymentSpec) }
                }
            }

            context("for a k8s resource with duplicate reference") {
                before {
                    deploymentSpec = deploymentSpec(
                        references = mutableSetOf("main-container", "sidecar-container"),
                        containers = arrayListOf(
                            mapOf(
                                "name" to "main",
                                "image" to "main-container"
                            ),
                            mapOf(
                                "name" to "sidecar",
                                "image" to "main-container"
                            )
                        )
                    )

                    deliveryConfig = deliveryConfig(resources = setOf(deploymentSpec))

                    every { repository.deliveryConfigFor(deploymentSpec.id) } returns deliveryConfig
                    every { repository.environmentFor(deploymentSpec.id) } returns deliveryConfig.environments.first()

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(0),
                            "test"
                        )
                    } returns "0.0.1"

                    every {
                        repository.latestVersionApprovedIn(
                            deliveryConfig,
                            artifacts.elementAt(1),
                            "test"
                        )
                    } returns "0.0.2"
                }

                test("a DuplicateReference exception is thrown") {
                    expectThrows<DuplicateReference> { this.invoke(deploymentSpec) }
                }
            }

            context("for a k8s resource with no containers") {
                val serviceSpec = serviceSpec()
                before {
                    deliveryConfig = deliveryConfig(resources = setOf(serviceSpec))
                    every { repository.deliveryConfigFor(deploymentSpec.id) } returns deliveryConfig
                    every { repository.environmentFor(deploymentSpec.id) } returns deliveryConfig.environments.first()
                }

                test("both containers are resolved correctly") {
                    val resolvedResource = this.invoke(serviceSpec)
                    expect {
                        that(resolvedResource).isEqualTo(serviceSpec)
                    }
                }
            }
        }
    }

    fun getImageWithName(r: Resource<K8sResourceSpec>, name: String) : String {
        val containers = ((r.spec.template.spec?.get("template") as Map<String, Any>)["spec"] as Map<String, Any>)["containers"] as List<Map<String, Any>>
        containers.filter { it["name"] == name }.also{ return it.first()["image"] as String}
    }

}


