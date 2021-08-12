package com.amazon.spinnaker.keel.k8s.verificationEvaluator

import com.amazon.spinnaker.keel.k8s.K8S_PROVIDER
import com.amazon.spinnaker.keel.k8s.SOURCE_TYPE
import com.amazon.spinnaker.keel.k8s.VERIFICATION_K8S_JOB
import com.amazon.spinnaker.keel.k8s.VERIFICATION_K8S_TYPE
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

class K8sJobEvaluator(
    private val taskLauncher: TaskLauncher,
    private val orcaService: OrcaService
) : VerificationEvaluator<K8sJobVerification> {
    private val log by lazy { LoggerFactory.getLogger(javaClass) }
    override val supportedVerification =
        VERIFICATION_K8S_JOB to K8sJobVerification::class.java

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
                description = "verifying ${context.version} in environment ${context.environmentName} with K8s $VERIFICATION_K8S_JOB",
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

    private fun generateOrcaK8sJob(application: String, verification: K8sJobVerification): OrcaJob {
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