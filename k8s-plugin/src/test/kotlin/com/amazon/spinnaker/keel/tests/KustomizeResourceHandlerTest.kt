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
import com.amazon.spinnaker.keel.k8s.model.K8sBlob
import com.amazon.spinnaker.keel.k8s.model.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.model.KustomizeResourceSpec
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.resolver.KustomizeResourceHandler
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
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*
import org.springframework.core.env.Environment as SpringEnv

@Suppress("UNCHECKED_CAST")
internal class KustomizeResourceHandlerTest : JUnit5Minutests {
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

    // DeliveryConfig returned by SQL
    private val deliveryConfigYaml = """
    ---
    name: demo1
    application: fnord
    serviceAccount: keeltest-service-account
    artifacts:
    - type: git
      reference: my-git-artifact
      tagVersionStrategy: semver-tag
      repoName: testRepo
      project: testProject
      gitType: github
      secretRef: git-repo
    environments:
    - name: test
      locations:
        account: deploy-experiments
        regions: []
      resources:
      - kind: k8s/kustomize@v1
        metadata:
          serviceAccount: keeltest-service-account
        spec:
          artifactSpec:
            ref: my-git-artifact
          metadata:
            application: fnord
          template:
            metadata:
              name: fnord-test
              namespace: flux-system
            spec:
              interval: 1m
              path: "./kustomize"
              prune: true
              targetNamespace: test
    """.trimIndent()

    private val sqlKustomizationYaml = """
    ---
    locations:
      account: my-k8s-west-account
      regions: []
    metadata:
      application: fnord
    artifactSpec:
      ref: my-git-artifact
    template:
      apiVersion: kustomize.toolkit.fluxcd.io/v1beta1
      kind: Kustomization
      metadata:
        application: fnord
        name: fnord-test
        namespace: flux-system
      spec:            
        interval: 1m
        path: "./kustomize"
        prune: true
        targetNamespace: test
    """.trimIndent()

    private val sqlKustomizationYamlWithArtifactOverride = """
    ---
    locations:
      account: my-k8s-west-account
      regions: []
    metadata:
      application: fnord
    artifactSpec:
      ref: my-git-artifact
      namespace: test
      interval: 10m
    template:
      apiVersion: kustomize.toolkit.fluxcd.io/v1beta1
      kind: Kustomization
      metadata:
        application: fnord
        name: fnord-test
        namespace: flux-system
      spec:            
        interval: 1m
        path: "./kustomize"
        prune: true
        targetNamespace: test
    """.trimIndent()

    private val clouddvierGitRepoYamlWithOverride = """
    apiVersion: source.toolkit.fluxcd.io/v1beta1
    kind: GitRepository
    metadata:
      annotations:
        artifact.spinnaker.io/location: flux-system
        artifact.spinnaker.io/name: git-github-testProject-testRepo-testEnv
        artifact.spinnaker.io/type: kubernetes/GitRepository.source.toolkit.fluxcd.io
        artifact.spinnaker.io/version: ''
        moniker.spinnaker.io/application: keeldemo
        moniker.spinnaker.io/cluster: >-
          GitRepository.source.toolkit.fluxcd.io
          git-github-nabuskey-md-flux-test-private
      labels:
        app.kubernetes.io/managed-by: spinnaker
        app.kubernetes.io/name: keeldemo
        md.spinnaker.io/plugin: k8s
      name: git-github-testProject-testRepo-testEnv
      namespace: test
    spec:
      interval: 10m
      ref:
        tag: 1.0.0
      secretRef:
        name: git-repo
      url: https://repo.url
    """.trimMargin()

    private val clouddriverKustomizationYaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: fnord
        |artifactSpec:
        |  ref: my-git-artifact
        |template:
        |  apiVersion: kustomize.toolkit.fluxcd.io/v1beta1
        |  kind: Kustomization
        |  metadata:
        |    annotations:
        |      artifact.spinnaker.io/name: git-github-testProject-testRepo
        |      artifact.spinnaker.io/location: flux-system
        |      moniker.spinnaker.io/application: keeldemo
        |    name: fnord-test
        |    namespace: flux-system
        |    application: fnord
        |    labels:
        |      md.spinnaker.io/plugin: k8s
        |  spec:
        |    interval: 1m
        |    path: ./kustomize
        |    prune: true
        |    targetNamespace: test
        |    sourceRef:
        |      kind: GitRepository
        |      name: git-github-testProject-testRepo-testEnv
        |      namespace: flux-system
    """.trimMargin()

    private val clouddvierGitRepoYaml = """
    apiVersion: source.toolkit.fluxcd.io/v1beta1
    kind: GitRepository
    metadata:
      annotations:
        artifact.spinnaker.io/location: flux-system
        artifact.spinnaker.io/name: git-github-testProject-testRepo-testEnv
        artifact.spinnaker.io/type: kubernetes/GitRepository.source.toolkit.fluxcd.io
        artifact.spinnaker.io/version: ''
        moniker.spinnaker.io/application: keeldemo
        moniker.spinnaker.io/cluster: >-
          GitRepository.source.toolkit.fluxcd.io
          git-github-nabuskey-md-flux-test-private
      labels:
        app.kubernetes.io/managed-by: spinnaker
        app.kubernetes.io/name: keeldemo
        md.spinnaker.io/plugin: k8s
      name: git-github-testProject-testRepo-testEnv
      namespace: flux-system
    spec:
      interval: 1m
      ref:
        tag: 0.1.5
      secretRef:
        name: git-repo
      url: https://repo.url
    """.trimMargin()

    private fun kustomizationResourceModel(lastApplied: KustomizeResourceSpec): K8sResourceModel {
        val mapper = jacksonObjectMapper()

        return K8sResourceModel(
            account = "my-k8s-west-account",
            artifacts = emptyList(),
            events = emptyList(),
            location = "my-k8s-west-account",
            manifest = K8sObjectManifest(
                apiVersion = FLUX_KUSTOMIZE_API_VERSION,
                kind = FLUX_KUSTOMIZE_KIND,
                metadata = mutableMapOf(
                    "name" to "fnord-test",
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

    fun tests() = rootContext<KustomizeResourceHandler> {
        fixture {
            KustomizeResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                publisher,
                orcaService,
                resolvers,
                repository
            )
        }

        val deliveryConfig =
            yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).makeDeliveryConfig()

        context("resource verification") {
            test("missing spec results in error") {
                val badSpec = yamlMapper.readValue(sqlKustomizationYaml, KustomizeResourceSpec::class.java)
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

            test("throws exception") {
                val badSpec = yamlMapper.readValue(sqlKustomizationYaml, KustomizeResourceSpec::class.java)
                badSpec.template.spec!!.remove("interval")
                val resource = resource(
                    kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                    spec = badSpec
                )
                runBlocking {
                    expectCatching { toResolvedType(resource) }.failed().isA<CannotResolveDesiredState>()
                }
            }
        }

        context("Kustomization resource creation") {
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

            context("with resource spec with artifact override") {
                val r = resource(
                    kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                    spec = yamlMapper.readValue(
                        sqlKustomizationYamlWithArtifactOverride,
                        KustomizeResourceSpec::class.java
                    )
                )
                before {
                    coEvery {
                        repository.deliveryConfigFor(r.id)
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

                test("toResolvedType succeeds with namespace and interval values updated") {
                    runBlocking {
                        val resource = resource(
                            kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                            spec = yamlMapper.readValue(
                                sqlKustomizationYamlWithArtifactOverride,
                                KustomizeResourceSpec::class.java
                            )
                        )
                        val resolved = toResolvedType(resource)
                        expectThat(resolved.items?.size).isEqualTo(2)
                        resolved.items?.forEach {
                            when (it.kind) {
                                FluxSupportedSourceType.GIT.fluxKind() -> {
                                    expectThat(it.apiVersion).isEqualTo(FLUX_SOURCE_API_VERSION)
                                    expectThat(it.namespace()).isEqualTo("test")
                                    expectThat(it.name()).isEqualTo("git-github-testProject-testRepo-testEnv")
                                    expectThat(it.spec as MutableMap)
                                        .hasEntry("interval", "10m")
                                        .hasEntry("url", "https://repo.url")
                                }
                                FLUX_KUSTOMIZE_KIND -> {
                                    expectThat(it.apiVersion).isEqualTo(FLUX_KUSTOMIZE_API_VERSION)
                                    val kustomizeSpec = it.spec as MutableMap<String, Any>
                                    expectThat(kustomizeSpec)
                                        .hasEntry("path", "./kustomize")
                                        .hasEntry("targetNamespace", "test")
                                        .hasEntry(
                                            "sourceRef", mutableMapOf(
                                                "kind" to FluxSupportedSourceType.GIT.fluxKind(),
                                                "name" to "git-github-testProject-testRepo-testEnv",
                                                "namespace" to "test"
                                            )
                                        )
                                }
                            }
                        }
                    }
                }
                test("deployment does not happen") {
                    val clouddriverKustomizeManifest =
                        yamlMapper.readValue(clouddriverKustomizationYaml, KustomizeResourceSpec::class.java)
                    (clouddriverKustomizeManifest.template.spec!!["sourceRef"] as MutableMap<String, Any>)["namespace"] =
                        "test"
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } returnsMany listOf(
                        gitRepositoryResourceModel(
                            yamlMapper.readValue(
                                clouddvierGitRepoYamlWithOverride,
                                K8sObjectManifest::class.java
                            )
                        ), kustomizationResourceModel(clouddriverKustomizeManifest)
                    )

                    runBlocking {
                        val resource = resource(
                            kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                            spec = yamlMapper.readValue(
                                sqlKustomizationYamlWithArtifactOverride,
                                KustomizeResourceSpec::class.java
                            )
                        )
                        val current = current(resource)
                        val desired = desired(resource)
                        val diff = DefaultResourceDiff(desired = desired, current = current)
                        expectThat(diff.hasChanges()).isFalse()
                    }
                }
            }

            context("with correct resource spec") {
                val spec = yamlMapper.readValue(sqlKustomizationYaml, KustomizeResourceSpec::class.java)
                val resource = resource(
                    kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                    spec = spec
                )
                before {
                    coEvery {
                        repository.deliveryConfigFor(resource.id)
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

                test("succeeds") {
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
                                FLUX_KUSTOMIZE_KIND -> {
                                    expectThat(it.apiVersion).isEqualTo(FLUX_KUSTOMIZE_API_VERSION)
                                    val kustomizeSpec = it.spec as MutableMap<String, Any>
                                    expectThat(kustomizeSpec)
                                        .hasEntry("path", "./kustomize")
                                        .hasEntry("targetNamespace", "test")
                                        .hasEntry(
                                            "sourceRef", mutableMapOf(
                                                "kind" to FluxSupportedSourceType.GIT.fluxKind(),
                                                "name" to "git-github-testProject-testRepo-testEnv",
                                                "namespace" to "flux-system"
                                            )
                                        )
                                }
                            }
                        }
                    }
                }
            }

            context("with correct specs") {
                val spec = yamlMapper.readValue(sqlKustomizationYaml, KustomizeResourceSpec::class.java)
                val resource = resource(
                    kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                    spec = spec
                )
                before {
                    // if try to distinguish calls here, you run into this bug: https://github.com/mockk/mockk/issues/288
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } returnsMany listOf(
                        gitRepositoryResourceModel(
                            yamlMapper.readValue(
                                clouddvierGitRepoYaml,
                                K8sObjectManifest::class.java
                            )
                        ),
                        kustomizationResourceModel(
                            yamlMapper.readValue(
                                clouddriverKustomizationYaml,
                                KustomizeResourceSpec::class.java
                            )
                        )
                    )
                    coEvery {
                        repository.deliveryConfigFor(resource.id)
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

                test("expected manifests returned") {
                    runBlocking {
                        val current = current(resource)
                        expectThat(current?.items?.size).isEqualTo(2)
                        current?.items?.forEach {
                            when (it.kind) {
                                FluxSupportedSourceType.GIT.fluxKind() -> {
                                    expectThat(it.metadata)
                                        .hasEntry("name", "git-github-testProject-testRepo-testEnv")
                                        .hasEntry("namespace", "flux-system")
                                    val repoSpec = it.spec as MutableMap<String, Any>
                                    expectThat(repoSpec)
                                        .hasEntry("url", "https://repo.url")
                                        .hasEntry("ref", mutableMapOf("tag" to "0.1.5"))
                                }
                                FLUX_KUSTOMIZE_KIND -> {
                                    expectThat(it.name()).isEqualTo("fnord-test")
                                    expectThat(it.metadata)
                                        .hasEntry("namespace", "flux-system")
                                    val kustomizeSpec = it.spec as MutableMap<String, Any>
                                    expectThat(kustomizeSpec)
                                        .hasEntry("path", "./kustomize")
                                        .hasEntry("targetNamespace", "test")
                                        .hasEntry(
                                            "sourceRef", mutableMapOf(
                                                "kind" to FluxSupportedSourceType.GIT.fluxKind(),
                                                "name" to "git-github-testProject-testRepo-testEnv",
                                                "namespace" to "flux-system"
                                            )
                                        )
                                }
                                else -> throw MisconfiguredObjectException("type ${it.kind} not expected")
                            }
                        }
                    }
                }

                test("deployment happens") {
                    runBlocking {
                        val current = current(resource)
                        val desired = desired(resource)
                        upsert(resource, DefaultResourceDiff(desired = desired, current = current))
                        val slot = slot<OrchestrationRequest>()

                        coVerify(exactly = 1) { orcaService.orchestrate(resource.serviceAccount, capture(slot)) }
                        expectThat(slot.captured.job.first()) {
                            get("type").isEqualTo("deployManifest")
                        }
                        verify(exactly = 1) {
                            publisher.publishEvent(
                                ArtifactVersionDeploying(
                                    "k8s:kustomize:my-k8s-west-account-flux-system-kustomization-fnord-test",
                                    "1.0.0"
                                )
                            )
                        }
                    }
                }

                test("deployment does not happen") {
                    val spec = yamlMapper.readValue(sqlKustomizationYaml, KustomizeResourceSpec::class.java)
                    spec.template
                    val resource = resource(
                        kind = KUSTOMIZE_RESOURCE_SPEC_V1.kind,
                        spec = spec
                    )
                    val repoManifest = yamlMapper.readValue(clouddvierGitRepoYaml, K8sObjectManifest::class.java)
                    repoManifest.spec?.set("ref", mutableMapOf("tag" to "1.0.0"))
                    clearMocks(cloudDriverK8sService)
                    coEvery {
                        cloudDriverK8sService.getK8sResource(
                            any(),
                            any(),
                            any(),
                            any(),
                        )
                    } returnsMany listOf(
                        gitRepositoryResourceModel(repoManifest),
                        kustomizationResourceModel(
                            yamlMapper.readValue(
                                clouddriverKustomizationYaml,
                                KustomizeResourceSpec::class.java
                            )
                        )
                    )


                    runBlocking {
                        val current = current(resource)
                        val desired = desired(resource)
                        val diff = DefaultResourceDiff(desired = desired, current = current)

                        expectThat(diff.diff.childCount()).isEqualTo(0)
                        expectThat(diff.hasChanges()).isFalse()
                    }
                }
            }
        }
    }
}
