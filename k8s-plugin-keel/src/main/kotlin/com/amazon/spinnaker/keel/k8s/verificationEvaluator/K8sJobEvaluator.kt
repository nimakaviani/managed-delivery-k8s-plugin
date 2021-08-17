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

package com.amazon.spinnaker.keel.k8s.verificationEvaluator

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.model.K8sJobVerification
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType.VERIFICATION
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.orca.OrcaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import java.util.*

class K8sJobEvaluator(
    private val taskLauncher: TaskLauncher,
    private val orcaService: OrcaService
) : VerificationEvaluator<K8sJobVerification> {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    override val supportedVerification =
        VERIFICATION_K8S_JOB_V1 to K8sJobVerification::class.java

    override fun evaluate(
        context: ArtifactInEnvironmentContext,
        verification: Verification,
        oldState: ActionState
    ): ActionState {
        val taskId = oldState.metadata["taskId"]
        requireNotNull(taskId) {
            "task ID not found in previous state. please check last K8s job executed by this verifyWith: $verification"
        }
        require(taskId is String) {
            "task ID could not be cast to string. please open an issue if you see this error message"
        }
        val response = runBlocking(Dispatchers.IO) {
            try {
                orcaService.getOrchestrationExecution(taskId)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    log.warn("task with task id $taskId not found. verification: $verification, metadata: ${oldState.metadata}")
                    null
                } else {
                    throw e
                }
            }
        }
        log.debug("response from orca for task $taskId: $response")
        response?.let {
            return when {
                response.status.isSuccess() -> oldState.copy(status = ConstraintStatus.PASS)
                response.status.isIncomplete() -> oldState.copy(ConstraintStatus.PENDING)
                else -> oldState.copy(ConstraintStatus.FAIL)
            }
        } ?: return oldState.copy(status = ConstraintStatus.FAIL)
    }

    override fun start(context: ArtifactInEnvironmentContext, verification: Verification): Map<String, Any?> {
        require(verification is K8sJobVerification) {
            "verification class must be ${K8sJobVerification::class.simpleName}. received: ${verification.javaClass.simpleName}"
        }
        log.debug("launching verification K8s job for ${context.deliveryConfig.application} env: ${context.environmentName}")
        val job = generateOrcaK8sJob(context.deliveryConfig.application, verification)
        return runBlocking {
            val launcherResponse = taskLauncher.submitJob(
                user = context.deliveryConfig.serviceAccount,
                application = context.deliveryConfig.application,
                notifications = emptySet(),
                subject = "verify environment, ${context.environmentName}, for ${context.deliveryConfig.application}",
                description = "verifying ${context.version} in environment ${context.environmentName} with K8s $VERIFICATION_K8S_JOB_V1",
                correlationId = verification.id,
                stages = listOf(job),
                type = VERIFICATION,
                artifacts = emptyList(),
                parameters = emptyMap()
            )
            mapOf(
                "taskId" to launcherResponse.id,
                "taskName" to launcherResponse.name
            )
        }
    }

    // need to randomize name otherwise subsequent jobs may fail with the same name in the same namespace
    // orca, clouddriver, or keel cleans finished jobs.
    private fun generateOrcaK8sJob(application: String, verification: K8sJobVerification): OrcaJob {
        verification.manifest[API_VERSION] = VERIFICATION_K8S_JOB_API_V1
        verification.manifest[KIND] = VERIFICATION_K8S_JOB_KIND

        val uuid = UUID.randomUUID().toString()
        val metadata = if (verification.manifest.containsKey("metadata")) {
            @Suppress("UNCHECKED_CAST")
            verification.manifest["metadata"] as MutableMap<String, Any>
        } else {
            mutableMapOf()
        }

        metadata[NAME] = verification.jobNamePrefix?.let {
            "${it}-${verification.id}-$uuid}".replace("/", "-").toLowerCase().take(252)
        } ?: "${verification.id}-$uuid}".replace("/", "-").toLowerCase().take(252)

        return OrcaJob(
            VERIFICATION_K8S_TYPE,
            mapOf(
                "account" to verification.account,
                "alias" to "runJob",
                "application" to application,
                "cloudProvider" to K8S_PROVIDER,
                "credentials" to verification.account,
                "manifest" to verification.manifest,
                "source" to SOURCE_TYPE
            )
        )
    }
}
