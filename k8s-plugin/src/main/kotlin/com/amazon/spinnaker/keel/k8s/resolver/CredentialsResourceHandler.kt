package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.*
import com.amazon.spinnaker.keel.k8s.exception.CouldNotRetrieveCredentials
import com.amazon.spinnaker.keel.k8s.exception.MisconfiguredObjectException
import com.amazon.spinnaker.keel.k8s.model.*
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import retrofit2.HttpException
import java.util.*

class CredentialsResourceHandler(
    cloudDriverK8sService: CloudDriverK8sService, taskLauncher: TaskLauncher, eventPublisher: EventPublisher,
    orcaService: OrcaService, resolvers: List<Resolver<*>>
) : GenericK8sResourceHandler<CredentialsResourceSpec, K8sCredentialManifest>(
    cloudDriverK8sService, taskLauncher,
    eventPublisher, orcaService, resolvers
) {
    override val supportedKind = CREDENTIALS_RESOURCE_SPEC_V1
    private val encoder: Base64.Encoder = Base64.getMimeEncoder()

    public override suspend fun toResolvedType(resource: Resource<CredentialsResourceSpec>): K8sCredentialManifest {
        try {
            require(!(resource.spec.template.data?.get(TYPE) as String?).isNullOrEmpty()) {"missing or empty \".metadata.type\" field for the credential"}
            require(!(resource.spec.template.data?.get(CLOUDDRIVER_ACCOUNT) as String?).isNullOrEmpty()) {"missing or empty \".metadata.account\" field for the credential"}
        } catch (e: Exception) {
            throw MisconfiguredObjectException(e.message!!)
        }

        val cred: GitRepoAccountDetails
        val clouddriverAccountName = resource.spec.template.data?.get(CLOUDDRIVER_ACCOUNT) as String
        val credType = resource.spec.template.data?.get(TYPE) as String
        try {
            cred = cloudDriverK8sService.getCredentialsDetails(
                resource.serviceAccount,
                clouddriverAccountName
            )
        } catch (e: HttpException) {
            if (e.code() >= 300) {
                throw CouldNotRetrieveCredentials(clouddriverAccountName, e)
            } else {
                throw e
            }
        }
        val data: FluxCredential
        // Priority: token > password > ssh key
        when {
            cred.token.isNotBlank() -> {
                log.debug("populating password with token in manifest")
                data = FluxCredential(
                    username = encoder.encodeToString(FLUX_SECRETS_TOKEN_USERNAME.toByteArray()),
                    password = encoder.encodeToString(cred.token.toByteArray())
                )
            }
            cred.username.isNotBlank() -> {
                log.debug("populating username and password in manifest")
                data = FluxCredential(
                    username = encoder.encodeToString(cred.username.toByteArray()),
                    password = encoder.encodeToString(cred.password.toByteArray())
                )
            }
            cred.sshPrivateKey.isNotBlank() -> {
                log.debug("populating private key in manifest")
                data = FluxCredential(
                    identity = encoder.encodeToString(cred.sshPrivateKey.toByteArray())
                )
            }
            else -> {
                log.info("token, username/password, or ssh key was not returned by clouddriver")
                data = FluxCredential()
            }
        }
        // setting strategy.spinnaker.io/versioned is needed to avoid creating secrets with versioned names e.g. testing1-v000
        return K8sCredentialManifest(
            SECRET_API_V1,
            SECRET,
            mapOf(
                "namespace" to resource.spec.namespace,
                "name" to "${credType}-${clouddriverAccountName}",
                "annotations" to mapOf("strategy.spinnaker.io/versioned" to "false")
            ),
            null,
            data.toK8sBlob() as K8sBlob?
        )
    }

    override suspend fun current(resource: Resource<CredentialsResourceSpec>): K8sCredentialManifest? =
        super.current(resource)?.let {
            val lastAppliedConfig = (it.metadata[ANNOTATIONS] as Map<String, String>)[K8S_LAST_APPLIED_CONFIG] as String
            return cleanup(jacksonObjectMapper().readValue(lastAppliedConfig))
        }

    override suspend fun upsert(
        resource: Resource<CredentialsResourceSpec>,
        diff: ResourceDiff<K8sCredentialManifest>
    ): List<Task> {
        return super.upsert(resource, diff)
    }

    override suspend fun getK8sResource(r: Resource<CredentialsResourceSpec>): K8sCredentialManifest? =
        cloudDriverK8sService.getK8sResource(r)?.manifest?.to<K8sCredentialManifest>()

    override suspend fun actuationInProgress(resource: Resource<CredentialsResourceSpec>): Boolean =
        resource.spec.template.data?.get(CLOUDDRIVER_ACCOUNT)?.let { accountName ->
            resource.spec.template.data?.get(TYPE)?.let { type ->
                log.debug(resource.toString())
                val ids =
                    orcaService.getCorrelatedExecutions("${type}-${accountName}")
                log.debug("actuation in progress? ${ids.isNotEmpty()}: $ids")
                ids.isNotEmpty()
            }
        } ?: false
}
