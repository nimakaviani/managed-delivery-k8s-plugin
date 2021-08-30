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

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sBlob
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.resolver.HelmResourceHandler
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.amazon.spinnaker.keel.tests.testUtils.generateYamlMapper
import com.amazon.spinnaker.keel.tests.testUtils.gitRepositoryResourceModel
import com.amazon.spinnaker.keel.tests.testUtils.makeDeliveryConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.plugin.CannotResolveDesiredState
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.springframework.http.HttpStatus
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*
import org.springframework.core.env.Environment as SpringEnv

@Suppress("UNCHECKED_CAST")
internal class HelmResourceHandlerTest : JUnit5Minutests {
    private val cloudDriverK8sService = mockk<CloudDriverK8sService>()
    private val orcaService = mockk<OrcaService>()
    private val publisher: EventPublisher = mockk(relaxUnitFun = true)
    private val repository = mockk<KeelRepository>()
    private val springEnv: SpringEnv = mockk(relaxUnitFun = true)

    private val taskLauncher = OrcaTaskLauncher(
        orcaService,
        repository,
        publisher,
        springEnv
    )
    private val yamlMapper = generateYamlMapper()

    private val resolvers: List<Resolver<*>> = listOf(
        K8sResolver()
    )


    @Suppress("UNCHECKED_CAST")
    fun tests() = rootContext<HelmResourceHandler> {
        val helmResourceYaml = this::class.java.classLoader.getResource("helm/helmResource.yml").readText()
        val deliveryConfigYaml = this::class.java.classLoader.getResource("helm/deliveryConfig.yml").readText()
        val clouddvierGitRepoYaml =
            this::class.java.classLoader.getResource("helm/clouddriverGitRepoResponse.yml").readText()
        val deliveryConfigWithoutArtifactYaml =
            this::class.java.classLoader.getResource("helm/deliveryConfigWithoutArtifact.yml").readText()

        val deliveryConfig =
            yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).makeDeliveryConfig()

        fixture {
            HelmResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                publisher,
                orcaService,
                resolvers,
                repository
            )
        }

        context("resource verification") {
            test("missing spec results in error") {
                val badSpec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                badSpec.template.spec = null
                expectCatching {
                    toResolvedType(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = badSpec
                        )
                    )
                }.failed().isA<CannotResolveDesiredState>()
            }

            test("missing template.spec.chart field results in error") {
                val badSpec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                badSpec.template.spec!!.remove("chart")
                expectCatching {
                    toResolvedType(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = badSpec
                        )
                    )
                }.failed().isA<CannotResolveDesiredState>()
            }
            test("missing template.spec.chart.spec field results in error") {
                val badSpec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                (badSpec.template.spec!!["chart"] as MutableMap<String, Any>).remove("spec")
                expectCatching {
                    toResolvedType(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = badSpec
                        )
                    )
                }.failed().isA<CannotResolveDesiredState>()
            }
            test("missing template.spec.chart.spec.chart field results in error") {
                val badSpec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                ((badSpec.template.spec!!["chart"] as MutableMap<String, Any>)["spec"] as MutableMap<String, String>).remove(
                    "chart"
                )
                expectCatching {
                    toResolvedType(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = badSpec
                        )
                    )
                }.failed().isA<CannotResolveDesiredState>()
            }
        }

        context("HelmRepository resource creation") {
            before {
                coEvery {
                    orcaService.orchestrate(
                        "keel@spinnaker",
                        any()
                    )
                } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
                every { repository.environmentFor(any()) } returns Environment("testEnv")
                every {
                    springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)
                } returns false
            }

            after {
                clearAllMocks()
            }

            context("with correct resource spec") {
                before {
                    coEvery {
                        repository.deliveryConfigFor(any())
                    } returns deliveryConfig
                    coEvery {
                        repository.latestVersionApprovedIn(any(), any(), "testEnv")
                    } returns "1.0.0"
                    coEvery {
                        repository.getArtifactVersion(any(), "1.0.0", null)
                    } returns PublishedArtifact(
                        name = deliveryConfig.artifacts.first().name,
                        type = FluxSupportedSourceType.GIT.name.toLowerCase(),
                        reference = deliveryConfig.artifacts.first().reference,
                        version = "1.0.0",
                        gitMetadata = GitMetadata(
                            commit = "123",
                            repo = Repo(
                                link = "https://repo.url"
                            )
                        )
                    )
                }

                test("no error") {
                    val resource = resource(
                        kind = HELM_RESOURCE_SPEC_V1.kind,
                        spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                    )
                    runBlocking {
                        val resolved = toResolvedType(resource)
                        expectThat(resolved.items?.size).isEqualTo(2)
                        resolved.items?.forEach {
                            when (it.kind) {
                                FluxSupportedSourceType.GIT.fluxKind() -> {
                                    expectThat(it.apiVersion).isEqualTo(FLUX_SOURCE_API_VERSION)
                                    expectThat(it.namespace()).isEqualTo("flux-system")
                                    expectThat(it.name()).isEqualTo("git-github-testProject-testRepo-testEnv")
                                    expectThat(it.spec as MutableMap)
                                        .hasEntry("interval", "1m")
                                        .hasEntry("url", "https://repo.url")
                                }
                                FLUX_HELM_KIND -> {
                                    expectThat(it.apiVersion).isEqualTo(FLUX_HELM_API_VERSION)
                                    val chartSpec =
                                        (it.spec!!["chart"] as MutableMap<String, Any>)["spec"] as MutableMap<String, Any>
                                    expectThat(chartSpec)
                                        .hasEntry(
                                            "sourceRef", mutableMapOf(
                                                "name" to "git-github-testProject-testRepo-testEnv",
                                                "kind" to FluxSupportedSourceType.GIT.fluxKind(),
                                                "namespace" to "flux-system"
                                            )
                                        )
                                }
                            }
                        }
                    }
                }

                test("deployment does not happen") {
                    clearMocks(cloudDriverK8sService)
                    val repoManifest = yamlMapper.readValue(clouddvierGitRepoYaml, K8sObjectManifest::class.java)
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            "GitRepository git-github-testProject-testRepo-testEnv",
                        )
                    } returns gitRepositoryResourceModel(repoManifest)

                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            "helmrelease fnord-test",
                        )
                    } returns helmResourceModel()

                    runBlocking {
                        val desired = desired(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            )
                        )

                        val current = current(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            )
                        )
                        val diff = DefaultResourceDiff(desired = desired, current = current)
                        println("---------current-----------")
                        println(diff.diff.canonicalGet(current))
                        println("---------desired-----------")
                        println(diff.diff.canonicalGet(desired))
                        expectThat(diff.diff.childCount()).isEqualTo(0)
                        expectThat(diff.hasChanges()).isFalse()
                    }
                }

                test("null returned when 404") {
                    clearMocks(cloudDriverK8sService)
                    val notFound: Response<Any> =
                        Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } throws HttpException(notFound)

                    runBlocking {
                        val current = current(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            )
                        )
                        expectThat(current).isNull()
                    }
                }

                test("insert works") {
                    clearMocks(cloudDriverK8sService)
                    val notFound: Response<Any> =
                        Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } throws HttpException(notFound)

                    val slot = slot<OrchestrationRequest>()

                    runBlocking {
                        val desired = desired(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            )
                        )

                        val current = current(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            )
                        )
                        val diff = DefaultResourceDiff(desired = desired, current = current)

                        upsert(
                            resource(
                                kind = HELM_RESOURCE_SPEC_V1.kind,
                                spec = yamlMapper.readValue(helmResourceYaml, HelmResourceSpec::class.java)
                            ), diff
                        )

                        coVerify(exactly = 1) { orcaService.orchestrate(any(), capture(slot)) }
                        coVerify(exactly = 1) {
                            publisher.publishEvent(
                                ArtifactVersionDeploying(
                                    "k8s:helm:my-k8s-west-account-flux-system-helmrelease-fnord-test",
                                    "1.0.0"
                                )
                            )
                        }
                        expectThat(slot.captured.application).isEqualTo("fnord")
                        expectThat(slot.captured.description).contains("List-HelmRelease-fnord-test")
                    }
                }
            }
        }

        context("spec without artifact") {
            val deliveryConfigWithoutArtifact =
                yamlMapper.readValue(deliveryConfigWithoutArtifactYaml, SubmittedDeliveryConfig::class.java)
                    .makeDeliveryConfig()
            val helmResourceWithoutArtifactYaml =
                this::class.java.classLoader.getResource("helm/helmResourceWithoutArtifact.yml").readText()

            before {
                coEvery {
                    repository.deliveryConfigFor(any())
                } returns deliveryConfigWithoutArtifact
                every { repository.environmentFor(any()) } returns Environment("testEnv")
            }

            test("desired state is resolved correctly") {
                val resource = resource(
                    kind = HELM_RESOURCE_SPEC_V1.kind,
                    spec = yamlMapper.readValue(helmResourceWithoutArtifactYaml, HelmResourceSpec::class.java)
                )
                runBlocking {
                    val resolved = toResolvedType(resource)
                    expectThat(resolved.items?.size).isEqualTo(1)
                    resolved.items?.first()?.let {
                        expectThat(it.apiVersion).isEqualTo(FLUX_HELM_API_VERSION)
                        expectThat(it.kind).isEqualTo(FLUX_HELM_KIND)
                    }
                }
            }

            test("null returned when 404") {
                clearMocks(cloudDriverK8sService)
                val notFound: Response<Any> =
                    Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                coEvery {
                    cloudDriverK8sService.getK8sResource(
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } throws HttpException(notFound)

                runBlocking {
                    val current = current(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = yamlMapper.readValue(helmResourceWithoutArtifactYaml, HelmResourceSpec::class.java)
                        )
                    )
                    expectThat(current).isNull()
                }
            }

            test("no diff") {
                clearMocks(cloudDriverK8sService)
                coEvery {
                    cloudDriverK8sService.getK8sResource(
                        any(),
                        any(),
                        any(),
                        "helmrelease fnord-test",
                    )
                } returns helmResourceModel("helm/clouddriverHelmResponseWithoutArtifact.yml")

                runBlocking {
                    val desired = desired(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = yamlMapper.readValue(helmResourceWithoutArtifactYaml, HelmResourceSpec::class.java)
                        )
                    )
                    val current = current(
                        resource(
                            kind = HELM_RESOURCE_SPEC_V1.kind,
                            spec = yamlMapper.readValue(helmResourceWithoutArtifactYaml, HelmResourceSpec::class.java)
                        )
                    )
                    val diff = DefaultResourceDiff(desired = desired, current = current)
                    expectThat(diff.hasChanges()).isFalse()
                }
            }
        }
    }

    private fun helmResourceModel(path: String? = null): K8sResourceModel {
        val clouddriverHelmYaml =
            this::class.java.classLoader.getResource(path ?: "helm/clouddriverHelmResponse.yml").readText()
        val lastApplied = yamlMapper.readValue(clouddriverHelmYaml, HelmResourceSpec::class.java)
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = FLUX_HELM_API_VERSION,
                kind = FLUX_HELM_KIND,
                metadata = mutableMapOf(
                    "name" to "fnord-test",
                    "annotations" to mapOf(
                        K8S_LAST_APPLIED_CONFIG to jacksonObjectMapper().writeValueAsString(lastApplied.template)
                    )
                ),
                spec = mutableMapOf<String, Any>() as K8sBlob
            ),
            metrics = emptyList(),
            moniker = null,
            name = "fnord",
            status = emptyMap(),
            warnings = emptyList()
        )
    }
}
