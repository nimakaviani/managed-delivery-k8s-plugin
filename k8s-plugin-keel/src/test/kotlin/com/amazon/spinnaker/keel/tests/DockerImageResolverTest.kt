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

package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.K8S_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.NAMESPACE_DEFAULT
import com.amazon.spinnaker.keel.k8s.exception.DockerImageNotFound
import com.amazon.spinnaker.keel.k8s.exception.DuplicateReference
import com.amazon.spinnaker.keel.k8s.exception.NotLinked
import com.amazon.spinnaker.keel.k8s.model.*
import com.amazon.spinnaker.keel.k8s.resolver.DockerImageResolver
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.docker.MultiReferenceContainerProvider
import com.netflix.spinnaker.keel.persistence.KeelRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

@Suppress("UNCHECKED_CAST")
internal class DockerImageResolverTest : JUnit5Minutests {
    val repository: KeelRepository = mockk()
    val clouddriverCache: CloudDriverCache = mockk()
    val clouddriverService: CloudDriverService = mockk()
    val cloudDriverK8sService: CloudDriverK8sService = mockk()

    private val artifacts = setOf(
        DockerArtifact(
            name = "spkr/main",
            reference = "main-container",
            tagVersionStrategy = SEMVER_TAG,
            deliveryConfigName = "mydeliveryconfig"
        ),
        DockerArtifact(
            name = "spkr/sidecar",
            reference = "sidecar-container",
            tagVersionStrategy = SEMVER_TAG,
            deliveryConfigName = "mydeliveryconfig"
        )
    )

    fun deploymentSpec(references: Set<String>, containers: List<Map<String, String>>) = Resource(
        kind = K8S_RESOURCE_SPEC_V1.kind,
        metadata = mapOf(
            "id" to "deployment",
            "application" to "fnord",
            "serviceAccount" to "keel@spinnaker"
        ),
        spec = K8sResourceSpec(
            container = MultiReferenceContainerProvider(
                references = references
            ),
            locations = SimpleLocations(
                account = "test",
                regions = emptySet()
            ),
            metadata = mapOf("application" to "fnord"),
            template = K8sObjectManifest(
                apiVersion = "apps/v1",
                kind = "Deployment",
                metadata = mutableMapOf(
                    "name" to "hello-kubernetes",
                    "namespace" to NAMESPACE_DEFAULT
                ),
                spec = mutableMapOf<String, Any>(
                    "template" to mutableMapOf(
                        "spec" to mutableMapOf(
                            "containers" to containers
                        )
                    )
                ) as K8sBlob
            ),
        )
    )

    fun serviceSpec() = Resource(
        kind = K8S_RESOURCE_SPEC_V1.kind,
        metadata = mapOf(
            "id" to "service",
            "application" to "fnord"
        ),
        spec = K8sResourceSpec(
            container = null,
            locations = SimpleLocations(
                account = "test",
                regions = emptySet()
            ),
            metadata = mapOf("application" to "fnord"),
            template = K8sObjectManifest(
                apiVersion = "v1",
                kind = "Service",
                metadata = mutableMapOf(
                    "name" to "hello-svc",
                    "namespace" to NAMESPACE_DEFAULT
                ),
                spec = mutableMapOf<String, Any>(
                    "template" to emptyMap<String, Any>()
                ) as K8sBlob
            )
        )
    )

    fun deliveryConfig(resources: Set<Resource<K8sResourceSpec>>) = DeliveryConfig(
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
            DockerImageResolver(repository, clouddriverCache, clouddriverService, cloudDriverK8sService)
        }

        lateinit var deploymentSpec: Resource<K8sResourceSpec>
        lateinit var deliveryConfig: DeliveryConfig

        context("for k8s delivery config manifests with approved artifacts") {
            before {
                coEvery { clouddriverService.findDockerImages("test-registry", "spkr/main", "0.0.1", null) } returns
                        listOf(DockerImage("test-registry", "spkr/main", "0.0.1", "sha:1111"))
                coEvery { clouddriverService.findDockerImages("test-registry", "spkr/sidecar", "0.0.2", null) } returns
                        listOf(DockerImage("test-registry", "spkr/sidecar", "0.0.2", "sha:2222"))
                coEvery { cloudDriverK8sService.findDockerImages(any(), any(), any(), any(), any(), any()) } returns
                        listOf(
                            ClouddriverDockerImage(
                                "test-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/main",
                                    "index.docker.io/spkr/main:0.0.1",
                                    "docker",
                                    "0.0.1"
                                ),
                                "sha:1111", "index.docker.io", "spkr/main", "0.0.1"
                            ),
                            ClouddriverDockerImage(
                                "test-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/sidecar",
                                    "index.docker.io/spkr/sidecar:0.0.1",
                                    "docker",
                                    "0.0.1"
                                ),
                                "sha:2222", "index.docker.io", "spkr/sidecar", "0.0.1"
                            ),
                            ClouddriverDockerImage(
                                "test-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/sidecar",
                                    "index.docker.io/spkr/sidecar:0.0.2",
                                    "docker",
                                    "0.0.2"
                                ),
                                "sha:2222", "index.docker.io", "spkr/sidecar", "0.0.2"
                            ),
                            ClouddriverDockerImage(
                                "junk-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/sidecar",
                                    "junk/spkr/main:0.0.2",
                                    "docker",
                                    "0.0.2"
                                ),
                                "sha:2222", "index.docker.io", "spkr/main", "0.0.2"
                            ),
                            ClouddriverDockerImage(
                                "test-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/main",
                                    "index.docker.io/spkr/junk:0.0.1",
                                    "docker",
                                    "0.0.1"
                                ),
                                "sha:1111", "index.docker.io", "spkr/junk", "0.0.1"
                            ),
                            ClouddriverDockerImage(
                                "test-registry",
                                Artifact(
                                    Metadata(emptyMap(), "index.docker.io"),
                                    "spkr/main",
                                    "index.docker.io/spkr/main:1.0.1",
                                    "docker",
                                    "1.0.1"
                                ),
                                "sha:1111", "index.docker.io", "spkr/main", "1.0.1"
                            )
                        )
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
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.1",
                            null
                        )
                    } returns getPublishedArtifact("0.0.1", "main", false)

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

                test("the main container is resolved correctly") {
                    val resolvedResource = this.invoke(deploymentSpec)
                    expect {
                        that(getImageWithName(resolvedResource, "main")).isEqualTo("index.docker.io/spkr/main:0.0.1")
                    }
                }

                context("for a k8s resource with a single container with metadata") {
                    every {
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.1",
                            null
                        )
                    } returns getPublishedArtifact("0.0.1", "main")

                    test("the main container is resolved correctly without calling clouddriver") {
                        val resolvedResource = this.invoke(deploymentSpec)
                        expect {
                            that(
                                getImageWithName(
                                    resolvedResource,
                                    "main"
                                )
                            ).isEqualTo("index.docker.io/spkr/main:0.0.1")
                        }
                        coVerify(exactly = 0) {
                            clouddriverService.findDockerImages("test-registry", "spkr/main", "0.0.1", null)
                        }
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

                    every {
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.1",
                            null
                        )
                    } returns getPublishedArtifact("0.0.1", "main", false)

                    every {
                        repository.getArtifactVersion(
                            artifacts.elementAt(1),
                            "0.0.2",
                            null
                        )
                    } returns getPublishedArtifact("0.0.2", "sidecar", false)
                }

                test("both containers are resolved correctly") {
                    val resolvedResource = this.invoke(deploymentSpec)
                    expect {
                        that(getImageWithName(resolvedResource, "main")).isEqualTo("index.docker.io/spkr/main:0.0.1")
                    }
                    expect {
                        that(
                            getImageWithName(
                                resolvedResource,
                                "sidecar"
                            )
                        ).isEqualTo("index.docker.io/spkr/sidecar:0.0.2")
                    }
                }

                context("for a k8s resource with multiple container with metadata") {
                    every {
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.1",
                            null
                        )
                    } returns getPublishedArtifact("0.0.1", "main")

                    every {
                        repository.getArtifactVersion(
                            artifacts.elementAt(1),
                            "0.0.2",
                            null
                        )
                    } returns getPublishedArtifact("0.0.2", "sidecar")

                    test("the main container is resolved correctly without calling clouddriver") {
                        val resolvedResource = this.invoke(deploymentSpec)
                        expect {
                            that(
                                getImageWithName(
                                    resolvedResource,
                                    "main"
                                )
                            ).isEqualTo("index.docker.io/spkr/main:0.0.1")
                        }
                        expect {
                            that(
                                getImageWithName(
                                    resolvedResource,
                                    "sidecar"
                                )
                            ).isEqualTo("index.docker.io/spkr/sidecar:0.0.2")
                        }

                        coVerify(exactly = 0) {
                            clouddriverService.findDockerImages("test-registry", "spkr/main", "0.0.1", null)
                            clouddriverService.findDockerImages("test-registry", "spkr/sidecar", "0.0.2", null)
                        }
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
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.1",
                            null
                        )
                    } returns getPublishedArtifact("0.0.1", "main")
                }

                test("a DuplicateReference exception is thrown") {
                    expectThrows<DuplicateReference> { this.invoke(deploymentSpec) }
                    coVerify(exactly = 0) {
                        clouddriverService.findDockerImages("test-registry", "spkr/main", "0.0.1", null)
                    }
                }
            }

            context("for a k8s resource with invalid docker reference") {
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
                    } returns "0.0.10"

                    every {
                        repository.getArtifactVersion(
                            artifacts.first(),
                            "0.0.10",
                            null
                        )
                    } returns getPublishedArtifact("0.0.10", "main", false)
                }

                test("a DockerImageNotFound exception is thrown") {
                    expectThrows<DockerImageNotFound> { this.invoke(deploymentSpec) }
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

    fun getImageWithName(r: Resource<K8sResourceSpec>, name: String): String {
        val containers =
            ((r.spec.template.spec?.get("template") as Map<String, Any>)["spec"] as Map<String, Any>)["containers"] as List<Map<String, Any>>
        containers.filter { it["name"] == name }.also { return it.first()["image"] as String }
    }

    private fun getPublishedArtifact(version: String, repo: String, metadata: Boolean = true): PublishedArtifact =
        PublishedArtifact(
            name = version,
            type = "DOCKER",
            reference = version,
            version = version,
            metadata = if (metadata) {
                mapOf(
                    "fullImagePath" to "index.docker.io/spkr/$repo:$version",
                    "clouddriverAccount" to "test-registry",
                    "registry" to "index.docker.io"
                )
            } else {
                emptyMap()
            }
        )
}


