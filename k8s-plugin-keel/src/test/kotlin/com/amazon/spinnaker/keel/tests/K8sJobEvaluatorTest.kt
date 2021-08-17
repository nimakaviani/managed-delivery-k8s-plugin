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
import com.amazon.spinnaker.keel.k8s.model.K8sJobVerification
import com.amazon.spinnaker.keel.k8s.verificationEvaluator.K8sJobEvaluator
import com.amazon.spinnaker.keel.tests.testUtils.getRandomString
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.Job
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import okhttp3.ResponseBody
import org.springframework.http.HttpStatus
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*
import java.time.Instant

internal class K8sJobEvaluatorTest : JUnit5Minutests {
    private val deliveryConfigYaml = """
    ---
    name: demo1
    application: fnord
    serviceAccount: keel@spinnaker.io
    artifacts:
      - name: example/service
        type: docker
        reference: my-docker-artifact
        tagVersionStrategy: semver-tag
    environments:
    - name: test
      verifyWith:
      - account: deploy-experiements
        type: k8s/job@v1
        manifest: 
          metadata:
            name: pi
            some: value
          spec:
            template:
              spec:
                containers:
                - name: pi
                  image: perl
                  command: ["perl",  "-Mbignum=bpi", "-wle", "print bpi(2000)"]
                restartPolicy: Never
            backoffLimit: 4

      locations:
        account: deploy-experiments
        regions: []
      resources:
      - kind: k8s/kustomize@v1
        metadata:
          serviceAccount: keeltest-service-account
        spec:
          artifactSpec:
            ref: my-docker-artifact
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

    private val yamlMapper = testUtils.generateYamlMapper()

    fun tests() = rootContext<K8sJobEvaluator> {
        val orcaService = mockk<OrcaService>()
        val taskLauncher = mockk<TaskLauncher>()

        fixture {
            K8sJobEvaluator(
                taskLauncher, orcaService
            )
        }

        after {
            clearAllMocks()
        }

        context("evaluate and everything works") {
            val deliveryConfig =
                yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).toDeliveryConfig()
            test("successful verification") {
                coEvery {
                    orcaService.getOrchestrationExecution("123", any())
                } returns ExecutionDetailResponse(
                    "123", "somename", "fnord", Instant.now(), null, null, OrcaExecutionStatus.SUCCEEDED
                )
                val actionState = ActionState(
                    ConstraintStatus.PENDING, Instant.now(), null, mapOf("taskId" to "123", "taskName" to "somename")
                )
                val result = evaluate(mockk(), deliveryConfig.environments.first().verifyWith.first(), actionState)

                expectThat(result.status).isEqualTo(ConstraintStatus.PASS)
                expectThat(result.metadata).isEqualTo(mapOf("taskId" to "123", "taskName" to "somename"))
            }

            test("pending verification") {
                coEvery {
                    orcaService.getOrchestrationExecution("123", any())
                } returns ExecutionDetailResponse(
                    "123", "somename", "fnord", Instant.now(), null, null, OrcaExecutionStatus.RUNNING
                )
                val actionState = ActionState(
                    ConstraintStatus.PASS, Instant.now(), null, mapOf("taskId" to "123", "taskName" to "somename")
                )
                val result = evaluate(mockk(), deliveryConfig.environments.first().verifyWith.first(), actionState)

                expectThat(result.status).isEqualTo(ConstraintStatus.PENDING)
            }
        }
        context("start task and everything works") {
            test("successful start of k8s job") {
                val deliveryConfig =
                    yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).toDeliveryConfig()
                val slot = slot<List<Job>>()
                coEvery {
                    taskLauncher.submitJob(any(), any(), any(), any(), any(), any(), capture(slot), any(), any(), any())
                } returns Task("123", "somename")
                val context = ArtifactInEnvironmentContext(
                    deliveryConfig, deliveryConfig.environments.first(), PublishedArtifact(
                        "artifactName", "Docker", "123`", "my-docker-artifact"
                    )
                )

                val result = start(context, deliveryConfig.environments.first().verifyWith.first())
                expectThat(result).isEqualTo(mapOf("taskId" to "123", "taskName" to "somename"))

                expectThat(slot.captured.size).isEqualTo(1)
                expectThat(slot.captured.first()["type"]).isEqualTo(VERIFICATION_K8S_TYPE)

                val manifest = slot.captured.first()["manifest"] as Map<String, Any>
                expectThat(manifest[API_VERSION]).isEqualTo(VERIFICATION_K8S_JOB_API_V1)
                expectThat(manifest[KIND]).isEqualTo(VERIFICATION_K8S_JOB_KIND)

                val metadata = manifest["metadata"] as Map<String, Any>
                expectThat(metadata[NAME]).isNotEqualTo("pi")
                expectThat(metadata["some"]).isEqualTo("value")
            }

            test("name is trimmed and name prefix applied") {
                val longString = getRandomString(240)
                val deliveryConfig =
                    yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).toDeliveryConfig()
                val verification = (deliveryConfig.environments.first().verifyWith.first() as K8sJobVerification)

                val slot = slot<List<Job>>()
                coEvery {
                    taskLauncher.submitJob(any(), any(), any(), any(), any(), any(), capture(slot), any(), any(), any())
                } returns Task("123", "somename")
                val context = ArtifactInEnvironmentContext(
                    deliveryConfig, deliveryConfig.environments.first(), PublishedArtifact(
                        "artifactName", "Docker", "123`", "my-docker-artifact"
                    )
                )
                start(context, verification.copy(account = longString, jobNamePrefix = "tEstIngPrefix"))
                val manifest = slot.captured.first()["manifest"] as Map<String, Any>
                val metadata = manifest["metadata"] as Map<String, Any>
                val name = metadata["name"] as String
                expectThat(name.length).isEqualTo(252)
                expectThat(name.startsWith("testingprefix")).isTrue()
                expectThat(name.toLowerCase()).isEqualTo(name)
                expectThat(name.contains("/")).isFalse()
                expectThat(metadata["some"]).isEqualTo("value")
            }
        }

        context("http failures returned by orca service") {
            val notFound: Response<Any> =
                Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
            val badRequest: Response<Any> =
                Response.error(HttpStatus.BAD_REQUEST.value(), ResponseBody.create(null, "fix your request"))
            val actionState = ActionState(
                ConstraintStatus.PENDING, Instant.now(), null, mapOf("taskId" to "123", "taskName" to "somename")
            )
            val deliveryConfig =
                yamlMapper.readValue(deliveryConfigYaml, SubmittedDeliveryConfig::class.java).toDeliveryConfig()
            test("404 results in failure") {
                coEvery {
                    orcaService.getOrchestrationExecution("123", "keel@spinnaker.io")
                } throws HttpException(notFound)

                val result = evaluate(mockk(), deliveryConfig.environments.first().verifyWith.first(), actionState)

                expectThat(result.status).isEqualTo(ConstraintStatus.FAIL)
            }

            test("http error is returned by evaluate") {
                coEvery {
                    orcaService.getOrchestrationExecution("123", "keel@spinnaker.io")
                } throws HttpException(badRequest)
                expectCatching {
                    evaluate(mockk(), deliveryConfig.environments.first().verifyWith.first(), actionState)
                }.failed().isA<HttpException>()
            }

            test("http error is returned by start") {
                coEvery {
                    orcaService.getOrchestrationExecution("123", "keel@spinnaker.io")
                } throws HttpException(notFound)
                coEvery {
                    taskLauncher.submitJob(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                } throws HttpException(badRequest)

                val context = ArtifactInEnvironmentContext(
                    deliveryConfig, deliveryConfig.environments.first(), PublishedArtifact(
                        "artifactName", "Docker", "123`", "my-docker-artifact"
                    )
                )
                expectCatching {
                    start(context, deliveryConfig.environments.first().verifyWith.first())
                }.failed().isA<HttpException>()
            }
        }
    }
}