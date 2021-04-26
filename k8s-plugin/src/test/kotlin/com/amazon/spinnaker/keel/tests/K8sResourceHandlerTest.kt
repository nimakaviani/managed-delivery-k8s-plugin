package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.resolver.K8sResourceHandler
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
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
import com.netflix.spinnaker.keel.test.resource
import okhttp3.ResponseBody
import de.danielbechler.diff.node.DiffNode
import de.danielbechler.diff.path.NodePath
import dev.minutest.rootContext
import io.mockk.*
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import org.springframework.http.HttpStatus
import org.springframework.core.env.Environment as SpringEnv
import retrofit2.Response
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.util.*

@Suppress("UNCHECKED_CAST")
internal class K8sResourceHandlerTest : JUnit5Minutests {
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
        |  application: test
        |template:
        |  apiVersion: "apps/v1"
        |  kind: Deployment
        |  metadata:
        |    name: hello-kubernetes
        |  spec:
        |    replicas: REPLICA
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
        |          image: nimak/helloworld:0.1
        |          ports:
        |          - containerPort: 8080
    """.trimMargin()

    private val spec = yamlMapper.readValue(yaml.replace("replicas: REPLICA", "replicas: 1"), K8sResourceSpec::class.java)
    private val resource = resource(
        kind = K8S_RESOURCE_SPEC_V1.kind,
        spec = spec
    )

    private fun resourceModel(replicas: Int = 1) : K8sResourceModel {
        val mapper = jacksonObjectMapper()
        val lastApplied = yamlMapper.readValue(yaml.replace("replicas: REPLICA", "replicas: ${replicas}"), K8sResourceSpec::class.java)
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = "apps/v1",
                kind = "Deployment",
                metadata = mapOf(
                    "name" to "hello-kubernetes",
                    "annotations" to mapOf(
                        K8S_LAST_APPLIED_CONFIG to mapper.writeValueAsString(lastApplied.template)
                    )
                ),
                spec = mutableMapOf<String, Any>() as K8sSpec
            ),
            metrics = emptyList(),
            moniker = null,
            name = "test",
            status = emptyMap(),
            warnings = emptyList()
        )
    }


    fun tests() = rootContext<K8sResourceHandler> {
        fixture {
            K8sResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                publisher,
                orcaService,
                resolvers
            )
        }

        before{
            coEvery { orcaService.orchestrate("keel@spinnaker", any()) } returns TaskRefResponse("/tasks/${UUID.randomUUID()}")
            every { repository.environmentFor(any()) } returns Environment("test")
            every {
                springEnv.getProperty("keel.notifications.slack", Boolean::class.java, true)
            } returns false
        }

        after {
            clearAllMocks()
        }

        context("K8s resource does not exist"){
            before {
                val notFound: Response<Any> = Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
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
                    get { spec?.get("replicas") }.isEqualTo(1)
                }

                expectThat(resources.first()) {
                    get{ name() }.isEqualTo("hello-kubernetes")
                }

                expectThat(resources.first()) {
                    get{ kindQualifiedName() }.isEqualTo("deployment hello-kubernetes")
                }
            }

            test("a deploying event fires") {
                runBlocking {
                    val current = current(resource)
                    val desired = desired(resource)
                    upsert(resource, DefaultResourceDiff(desired = desired, current = current))
                }

                verify(atLeast = 1) { publisher.publishEvent(ArtifactVersionDeploying(resource.id,  "0.1")) }
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
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(2)
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

        context("actuation in progress") {
            before {
                coEvery { orcaService.getCorrelatedExecutions("hello-kubernetes") } returnsMany listOf(
                    listOf("executionId1"), emptyList()
                )
            }

            test("should indicate if actuation is in progress") {
                runBlocking {
                    val result = actuationInProgress(resource)
                    val result2 = actuationInProgress(resource)
                    expectThat(result).isTrue()
                    expectThat(result2).isFalse()
                }
            }
        }
    }
}