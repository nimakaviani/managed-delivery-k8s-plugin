package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.ResourceNotReady
import com.amazon.spinnaker.keel.k8s.model.Condition
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.resolver.K8sResourceHandler
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.model.Status
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
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
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*
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
    private val configMapYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: "v1"
        |  kind: ConfigMap
        |  metadata:
        |    name: hello-kubernetes
        |    namespace: hello
        |    labels:
        |      md.spinnaker.io/plugin: k8s
        |  data:
        |    replicas: 123
        |    game.properties: food=ramen
    """.trimMargin()

    private val listYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: "v1"
        |  kind: List
        |  metadata:
        |    name: hello-kubernetes
        |    namespace: hello
        |  items:
        |  - apiVersion: v1
        |    kind: Service
        |    type: ClusterIP
        |    metadata:
        |      name: hello-kubernetes
        |      labels:
        |        labelKey: labelValue
        |    spec:
        |      not: used
        |  - apiVersion: "apps/v1"
        |    kind: Deployment
        |    metadata:
        |      name: hello-kubernetes
        |    spec:
        |      not: used
    """.trimMargin()

    private val dataYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: "v1"
        |  kind: NotConfigMap
        |  metadata:
        |    name: hello-kubernetes
        |    namespace: hello
        |  data:
        |    please: do-not-touch
    """.trimMargin()

    private val yaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
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

    private val expectedYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |template:
        |  apiVersion: "apps/v1"
        |  kind: Deployment
        |  metadata:
        |    name: hello-kubernetes
        |    labels:
        |      md.spinnaker.io/plugin: k8s
        |  spec:
        |    replicas: REPLICA
        |    selector:
        |      matchLabels:
        |        app: hello-kubernetes
        |    template:
        |      metadata:
        |        labels:
        |          app: hello-kubernetes
        |          md.spinnaker.io/plugin: k8s
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

    private fun resourceModel(k8sManifest: K8sObjectManifest , specMap: MutableMap<String, Any?> = mutableMapOf(),) : K8sResourceModel {
        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = "apps/v1",
                kind = "Deployment",
                metadata = mutableMapOf(
                    "name" to "hello-kubernetes",
                    "annotations" to mapOf(
                        K8S_LAST_APPLIED_CONFIG to jacksonObjectMapper().writeValueAsString(k8sManifest)
                    ),
                ),
                spec = specMap
            ),
            metrics = emptyList(),
            moniker = null,
            name = "fnord",
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
            val resourceSpec = yamlMapper.readValue(expectedYaml.replace("replicas: REPLICA", "replicas: 1"), K8sResourceSpec::class.java)
            var resourceModel = resourceModel(resourceSpec.template, mutableMapOf("image" to "teset/test:0.1"))
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

            context("fetching current resource") {
                before {
                    runBlocking {
                        current(resource)
                    }
                }
                test("fires a deployed event") {
                    verify(atLeast = 1) { publisher.publishEvent(ArtifactVersionDeployed(resource.id, "0.1")) }
                }
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

        context("the K8s resource has been updated") {
            val resourceSpec = yamlMapper.readValue(expectedYaml.replace("replicas: REPLICA", "replicas: 2"), K8sResourceSpec::class.java)
            before {
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                    resourceSpec.template
                )
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

        context ("Diffing resources") {
            context("when there is an annotation diff") {
                val resourceSpec = yamlMapper.readValue(expectedYaml.replace("replicas: REPLICA", "replicas: 1"), K8sResourceSpec::class.java)
                context("when it is one of the expected annotations") {
                    before{
                        (resourceSpec.template.metadata)["annotations"] = mutableMapOf("artifact.spinnaker.io/location" to "something")
                        coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                            resourceSpec.template
                        )
                    }
                    test("there is no diff") {
                        runBlocking {
                            val current = current(resource)
                            val desired = desired(resource)
                            val diff = DefaultResourceDiff(desired = desired, current = current)
                            expectThat(diff.hasChanges()).isFalse()
                        }
                    }
                }
                context("when it is not one of the expected annotations") {
                    before{
                        (resourceSpec.template.metadata)["annotations"] = mutableMapOf("random" to "something")
                        coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                            resourceSpec.template
                        )
                    }
                    test("there is a diff"){
                        runBlocking {
                            val current = current(resource)
                            val desired = desired(resource)
                            val diff = DefaultResourceDiff(desired = desired, current = current)
                            expectThat(diff.hasChanges()).isTrue()
                        }
                    }
                }
            }

            context("when there is an label diff") {
                val resourceSpec = yamlMapper.readValue(expectedYaml.replace("replicas: REPLICA", "replicas: 1"), K8sResourceSpec::class.java)
                context("when it is one of the expected labels") {
                    before{
                        ((resourceSpec.template.metadata)[LABELS] as MutableMap<String, String>)["app.kubernetes.io/name"] to "something"
                        coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                            resourceSpec.template
                        )
                    }
                    test("there is no diff") {
                        runBlocking {
                            val current = current(resource)
                            val desired = desired(resource)
                            val diff = DefaultResourceDiff(desired = desired, current = current)
                            expectThat(diff.hasChanges()).isFalse()
                        }
                    }
                }
                context("when it is not one of the expected labels") {
                    before{
                        (resourceSpec.template.metadata)[LABELS] = mutableMapOf("random" to "something")
                        coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                            resourceSpec.template
                        )
                    }
                    test("there is a diff"){
                        runBlocking {
                            val current = current(resource)
                            val desired = desired(resource)
                            val diff = DefaultResourceDiff(desired = desired, current = current)
                            expectThat(diff.hasChanges()).isTrue()
                        }
                    }
                }
            }
        }

        context("when spec with data field that is not secret or configMap is used") {
            val desired = yamlMapper.readValue(dataYaml, K8sResourceSpec::class.java)
            val desiredSpec = resource(
                kind = K8S_RESOURCE_SPEC_V1.kind,
                spec = desired
            )
            val current = yamlMapper.readValue(configMapYaml, K8sResourceSpec::class.java)
            val currentSpec = resource(
                kind = K8S_RESOURCE_SPEC_V1.kind,
                spec = current
            )

            before {
                coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                    current.template
                )
            }

            test("spinnaker annotation is not added") {
                runBlocking {
                    val currentResource = current(currentSpec)
                    val desiredResource = desired(desiredSpec)
                    upsert(desiredSpec, DefaultResourceDiff(desired = desiredResource, current = currentResource))
                    val slots = mutableListOf<OrchestrationRequest>()
                    coVerify { orcaService.orchestrate("keel@spinnaker", capture(slots)) }

                    val resources = slots.first().job.first()["manifests"] as List<K8sObjectManifest>
                    val metadata = resources.first().metadata
                    expectThat(metadata)
                        .hasEntry("name", "hello-kubernetes")
                        .hasEntry("namespace", "hello")
                        .hasEntry(LABELS, mutableMapOf(
                            MANAGED_DELIVERY_PLUGIN_LABELS.first().first to MANAGED_DELIVERY_PLUGIN_LABELS.first().second))
                        .hasSize(3)
                }
            }
        }

        context("when configmap is used") {
            val deployed = yamlMapper.readValue(configMapYaml, K8sResourceSpec::class.java)
            deployed.template.metadata["annotations"] = mapOf("strategy.spinnaker.io/versioned" to "false")

            val desired = yamlMapper.readValue(configMapYaml, K8sResourceSpec::class.java)
            val desiredSpec = resource(
                kind = K8S_RESOURCE_SPEC_V1.kind,
                spec = desired
            )
            val specWithAnnotation = yamlMapper.readValue(configMapYaml, K8sResourceSpec::class.java)
            specWithAnnotation.template.metadata["annotations"] = mapOf("dont-change-me" to "please")
            val annotationSpec = resource(
                kind = K8S_RESOURCE_SPEC_V1.kind,
                spec = specWithAnnotation
            )

            context("when configMap with annotation is present") {
                before {
                    coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                        specWithAnnotation.template
                    )
                }

                test("spinnaker annotation is added") {
                    runBlocking {
                        val currentResource = current(annotationSpec)
                        val desiredResource = desired(desiredSpec)
                        upsert(desiredSpec, DefaultResourceDiff(desired = desiredResource, current = currentResource))
                        val slots = mutableListOf<OrchestrationRequest>()
                        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slots)) }

                        val resources = slots.first().job.first()["manifests"] as List<K8sObjectManifest>
                        expectThat(resources.first().metadata.containsKey("annotations")).isTrue()

                        val annotations = resources.first().metadata["annotations"] as Map<String, Any>
                        expectThat(annotations["strategy.spinnaker.io/versioned"]).isEqualTo("false")
                    }
                }
            }

            context("when no configMap with annotation is present") {
                before{
                    coEvery { cloudDriverK8sService.getK8sResource(any(), any(), any(), any()) } returns resourceModel(
                        deployed.template
                    )
                }

                test("eventPublisher is never called") {
                    runBlocking {
                        current(desiredSpec)
                        verify(exactly = 0) {publisher.publishEvent(any())}
                    }
                }

                test("there is no diff") {
                    runBlocking {
                        val currentResource = current(desiredSpec)
                        val desiredResource = desired(desiredSpec)
                        val diff = DefaultResourceDiff(desired = desiredResource, current = currentResource)
                        expectThat(diff.hasChanges()).isFalse()
                    }
                }

                test("annotation in desired spec is preserved") {
                    runBlocking {
                        val currentResource = current(annotationSpec)
                        val desiredResource = desired(annotationSpec)
                        upsert(annotationSpec, DefaultResourceDiff(desired = desiredResource, current = currentResource))
                        val slots = mutableListOf<OrchestrationRequest>()
                        coVerify { orcaService.orchestrate("keel@spinnaker", capture(slots)) }

                        val resources = slots.first().job.first()["manifests"] as List<K8sObjectManifest>
                        expectThat(resources.first().metadata.containsKey("annotations")).isTrue()

                        val annotations = resources.first().metadata["annotations"] as Map<String, Any>
                        expectThat(annotations["strategy.spinnaker.io/versioned"]).isEqualTo("false")
                        expectThat(annotations["dont-change-me"]).isEqualTo("please")
                    }
                }
            }
        }

        context("when spec with items are used") {
            val current = yamlMapper.readValue(listYaml, K8sResourceSpec::class.java)
            val currentSpec = resource(
                kind = K8S_RESOURCE_SPEC_V1.kind,
                spec = current
            )

            test("each item should have the plugin labels") {
                runBlocking {
                    val spec = desired(currentSpec)
                    expectThat(spec.items?.size).isEqualTo(2)
                    spec.items?.forEach {
                        val labels = it.metadata[LABELS] as MutableMap<String, String>
                        // ensure existing label is preserved for the Service kind
                        if (it.kind == "Service") {
                            expectThat(labels).hasEntry("labelKey", "labelValue")
                        }
                        expectThat(labels)
                            .hasEntry(MANAGED_DELIVERY_PLUGIN_LABELS.first().first, MANAGED_DELIVERY_PLUGIN_LABELS.first().second)
                    }
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