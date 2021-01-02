package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.*
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import org.springframework.context.ApplicationEventPublisher
import com.netflix.spinnaker.keel.test.resource
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.path.NodePath
import dev.minutest.rootContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import java.util.*

@Suppress("UNCHECKED_CAST")
internal class K8sResourceHandlerTest : JUnit5Minutests {
    private val cloudDriverK8sService = mockk<CloudDriverK8sService>()
    private val orcaService = mockk<OrcaService>()
    private val publisher: EventPublisher = mockk(relaxUnitFun = true)
    private val repository = mockk<KeelRepository> {
        // we're just using this to get notifications
        every { environmentFor(any()) } returns Environment("test")
    }

    private val taskLauncher = OrcaTaskLauncher(
        orcaService,
        repository,
        publisher
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
        |template:
        |  apiVersion: "apps/v1"
        |  kind: Deployment
        |  metadata:
        |    name: hello-kubernetes
        |    annotations:
        |      moniker.spinnaker.io/application: spinmd
        |  spec:
        |    replicas: 2
        |    selector:
        |      matchLabels:
        |        app: hello-kubernetes
        |    template:
        |      metadata:
        |        labels:
        |          app: hello-kubernetes
        |      spec:
        |        containers:
        |        - name: hello-kubernetes
        |          image: paulbouwer/hello-kubernetes:1.8
        |          ports:
        |          - containerPort: 8080
    """.trimMargin()

    private fun resourceModel(replicas: Int = 2) : K8sResourceModel {
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = Manifest(
                apiVersion = "apps/v1",
                kind = "Deployment",
                metadata = mapOf(
                    "name" to "hello-kubernetes",
                    "annotations" to mapOf(
                        "moniker.spinnaker.io/application" to "spinmd"
                    )
                ),
                spec = mapOf(
                    "replicas" to replicas,
                    "selector" to mapOf(
                        "matchLabels" to mapOf(
                            "app" to "hello-kubernetes"
                        )
                    ),
                    "template" to mapOf(
                        "metadata" to mapOf(
                          "labels" to mapOf(
                                "app" to "hello-kubernetes"
                                )
                            ),
                      "spec" to mapOf(
                        "containers" to listOf(
                            mapOf(
                                "name" to "hello-kubernetes",
                                "image" to "paulbouwer/hello-kubernetes:1.8",
                                "ports" to listOf(
                                    mapOf("containerPort" to 8080)
                                )
                            )
                        )
                      )
                    )
                )
            ),
            metrics = emptyList(),
            moniker = null,
            name = "spinmd",
            status = emptyMap(),
            warnings = emptyList()
        )
    }

    private val spec = yamlMapper.readValue(yaml, K8sResourceSpec::class.java)
    private val resource = resource(
        kind = com.amazon.spinnaker.keel.k8s.K8S_RESOURCE_SPEC_V1.kind,
        spec = spec
    )

    fun tests() = rootContext<K8sResourceHandler> {
        fixture {
            K8sResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                resolvers
            )
        }

        before{
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
        }

        context("K8s resource does not exist"){
            before {
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns
                        K8sResourceModel(
                            "account", emptyList(), emptyList(), "default",
                            Manifest("test", "test", emptyMap(), mutableMapOf()),
                            emptyList(), Moniker("test"), "name", emptyMap(), emptyList()
                        )
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

                val resources = slot.captured.job.first()["manifests"] as List<K8sResourceTemplate>
                expectThat(resources.first()) {
                    get { spec["replicas"] }.isEqualTo(2)
                }

                expectThat(resources.first()) {
                    get{ name }.isEqualTo("deployment-hello-kubernetes")
                }
            }
        }

        context("the K8s resource has been created"){
            before {
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel()
            }

            test("the diff is clean") {
                val diff = runBlocking {
                    val current = current(resource)
                    val desired = desired(resource)
                    DefaultResourceDiff(desired = desired, current = current)
                }

                expectThat(diff.diff.childCount()).isEqualTo(0)
            }
        }

        context("the K8s resource has been updated") {
            before {
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(1)
            }

            test("the diff reflects the new spec and is upserted") {
                runBlocking {
                    val current = current(resource)
                    val desired = desired(resource)
                    val diff = DefaultResourceDiff(desired = desired, current = current)

                    expectThat(diff.diff) {
                        get { childCount() }.isEqualTo(1)
                        get {
                            getChild(
                                NodePath.startBuilding().propertyName("spec").mapKey("replicas").build()
                            ).state
                        }.isEqualTo(DiffNode.State.CHANGED)
                    }

                    upsert(resource, diff)
                }

                val slot = slot<OrchestrationRequest>()
                coVerify { orcaService.orchestrate("keel@spinnaker", capture(slot)) }

                expectThat(slot.captured.job.first()) {
                    get("type").isEqualTo("deployManifest")
                }
            }
        }
    }
}