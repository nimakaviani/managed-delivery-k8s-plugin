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
import com.amazon.spinnaker.keel.k8s.exception.MisconfiguredObjectException
import com.amazon.spinnaker.keel.k8s.exception.ResourceNotReady
import com.amazon.spinnaker.keel.k8s.model.*
import com.amazon.spinnaker.keel.k8s.resolver.HelmResourceHandler
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import com.netflix.spinnaker.keel.test.resource
import okhttp3.ResponseBody
import dev.minutest.rootContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import org.springframework.http.HttpStatus
import org.springframework.core.env.Environment as SpringEnv
import retrofit2.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

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
    private val yamlMapper = configuredYamlMapper()

    private val resolvers: List<Resolver<*>> = listOf(
        K8sResolver()
    )

    private val yaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  metadata:
        |    name: hello-kubernetes
        |  spec:
        |    url: some-url
    """.trimMargin()

    private val fullYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: helm.toolkit.fluxcd.io/v2beta1
        |  kind: HelmRelease
        |  metadata:
        |    name: hello-kubernetes
        |  spec:
        |    url: some-url
    """.trimMargin()

    private val wrongYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: something
        |  kind: HelmRelease
        |  metadata:
        |    name: hello-kubernetes
        |  spec:
        |    url: some-url
    """.trimMargin()

    private val expectedYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: helm.toolkit.fluxcd.io/v2beta1
        |  kind: HelmRelease
        |  metadata:
        |    name: hello-kubernetes
        |    labels:
        |      md.spinnaker.io/plugin: k8s
        |  spec:
        |    url: some-url
    """.trimMargin()

    private fun resourceModel() : K8sResourceModel {
        val mapper = jacksonObjectMapper()
        val lastApplied = yamlMapper.readValue(expectedYaml, HelmResourceSpec::class.java)
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = "source.toolkit.fluxcd.io/v1beta1",
                kind = "HelmRepository",
                metadata = mutableMapOf(
                    "name" to "hello-kubernetes",
                    "annotations" to mapOf(
                        K8S_LAST_APPLIED_CONFIG to mapper.writeValueAsString(lastApplied.template)
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

    fun tests() = rootContext<HelmResourceHandler> {
        fixture {
            HelmResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                publisher,
                orcaService,
                resolvers
            )
        }

        context("HelmRepository resource creation") {
            before {
                coEvery {
                    orcaService.orchestrate(
                        "keel@spinnaker",
                        any()
                    )
                } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
                every { repository.environmentFor(any()) } returns Environment("test")
                every {
                    springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)
                } returns false
            }

            after {
                clearAllMocks()
            }

            context("with invalid resource spec") {
                var spec = yamlMapper.readValue(wrongYaml, HelmResourceSpec::class.java)
                var resource = resource(
                    kind = HELM_RESOURCE_SPEC_V1.kind,
                    spec = spec
                )
                before {
                    val notFound: Response<Any> =
                        Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                    coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } throws
                            HttpException(notFound)
                }

                test("throws exception") {
                    runBlocking {
                        expectCatching { toResolvedType(resource) }.failed().isA<MisconfiguredObjectException>()
                    }
                }
            }

            context("with extended resource spec") {
                var spec = yamlMapper.readValue(fullYaml, HelmResourceSpec::class.java)
                var resource = resource(
                    kind = HELM_RESOURCE_SPEC_V1.kind,
                    spec = spec
                )
                before {
                    val notFound: Response<Any> =
                        Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                    coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } throws
                            HttpException(notFound)
                }

                test("succeeds") {
                    runBlocking {
                        expectCatching { toResolvedType(resource) }.succeeded()
                    }
                }
            }

            context("with valid resource spec") {
                var spec = yamlMapper.readValue(yaml, HelmResourceSpec::class.java)
                var resource = resource(
                    kind = HELM_RESOURCE_SPEC_V1.kind,
                    spec = spec
                )
                before {
                    val notFound: Response<Any> =
                        Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                    coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } throws
                            HttpException(notFound)
                }

                test("the resource is created with a generated defaultAction as none are in the spec") {
                    runBlocking {
                        val current = current(resource)
                        val desired = desired(resource)
                        upsert(resource, DefaultResourceDiff(desired = desired, current = current))
                    }

                    val slot = slot<OrchestrationRequest>()
                    coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

                    expectThat(slot.captured.job.first()) {
                        get("type").isEqualTo("deployManifest")
                    }

                    val resources = slot.captured.job.first()["manifests"] as List<K8sObjectManifest>
                    expectThat(resources.first()) {
                        get { name() }.isEqualTo("hello-kubernetes")
                    }

                    expectThat(resources.first()) {
                        get { kindQualifiedName() }.isEqualTo("helmrelease hello-kubernetes")
                    }
                }

                test("resolving a diff creates a new k8s resource") {
                    runBlocking {
                        val current = current(resource)
                        val desired = desired(resource)
                        upsert(resource, DefaultResourceDiff(desired = desired, current = current))
                    }

                    val slot = slot<OrchestrationRequest>()
                    coVerify { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }

                    expectThat(slot.captured.job.first()) {
                        get("type").isEqualTo("deployManifest")
                    }
                }


                context("the K8s resource has been created") {
                    val resourceModel = resourceModel()
                    before {
                        coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel
                    }

                    test("the diff is clean") {
                        val diff = runBlocking {
                            val current = current(resource)
                            val desired = desired(resource)
                            DefaultResourceDiff(desired = desired, current = current)
                        }

                        expectThat(diff.diff.childCount()).isEqualTo(0)
                    }

                    context("when status is set") {
                        context("with a healthy status") {
                            before {
                                resourceModel.manifest.status = Status(
                                    conditions = arrayOf<Condition>(Condition(type = "Ready", status = "True"))
                                )
                            }

                            test("deploying the resource succeeds and fires a healthy event"){
                                runBlocking {
                                    expectCatching { current(resource) }.succeeded()
                                }
                                verify(atLeast = 1) { publisher.publishEvent(ResourceHealthEvent(resource, true)) }
                            }
                        }
                        context("with an unhealthy status") {
                            before {
                                resourceModel.manifest.status = Status(
                                    conditions = arrayOf<Condition>(Condition(type = "Ready", status = "False"))
                                )
                            }

                            test("deploying the resource fails and fires an unhealthy event"){
                                runBlocking {
                                    expectCatching { current(resource) }.failed().isA<ResourceNotReady>()
                                }
                                verify(atLeast = 1) { publisher.publishEvent(ResourceHealthEvent(resource, false)) }
                            }
                        }
                    }
                }
            }
        }
    }
}