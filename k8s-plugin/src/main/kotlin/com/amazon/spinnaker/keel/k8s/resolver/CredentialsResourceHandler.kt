package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.CREDENTIALS_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.K8sData
import com.amazon.spinnaker.keel.k8s.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.exception.CouldNotRetrieveCredentials
import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
import com.amazon.spinnaker.keel.k8s.model.GitRepoAccountDetails
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import retrofit2.HttpException
import java.util.*

class CredentialsResourceHandler(
    cloudDriverK8sService: CloudDriverK8sService, taskLauncher: TaskLauncher, eventPublisher: EventPublisher,
    orcaService: OrcaService, resolvers: List<Resolver<*>>
) : GenericK8sResourceHandler<CredentialsResourceSpec, K8sObjectManifest>(
    cloudDriverK8sService, taskLauncher,
    eventPublisher, orcaService, resolvers
) {
    override val supportedKind = CREDENTIALS_RESOURCE_SPEC_V1
    private val encoder: Base64.Encoder = Base64.getMimeEncoder()

    public override suspend fun toResolvedType(resource: Resource<CredentialsResourceSpec>): K8sObjectManifest {
        val cred: GitRepoAccountDetails
        try {
            cred = cloudDriverK8sService.getCredentialsDetails(
                resource.serviceAccount,
                resource.spec.template.data?.account as String
            )
        } catch (e: HttpException) {
            if (e.code() >= 300) {
                throw CouldNotRetrieveCredentials(resource.spec.template.data?.account as String, e)
            } else {
                throw e
            }
        }
        val data: K8sData
        // Priority: token > password > ssh key
        when {
            cred.token.isNotBlank() -> {
                log.debug("populating token with username in manifest")
                data = K8sData(
                    username = encoder.encodeToString(cred.token.toByteArray()),
                    password = ""
                )
            }
            cred.username.isNotBlank() -> {
                log.debug("populating username and password in manifest")
                data = K8sData(
                    username = encoder.encodeToString(cred.username.toByteArray()),
                    password = encoder.encodeToString(cred.password.toByteArray())
                )
            }
            cred.sshPrivateKey.isNotBlank() -> {
                log.debug("populating private key in manifest")
                data = K8sData(
                    identity = encoder.encodeToString(cred.sshPrivateKey.toByteArray())
                )
            }
            else -> {
                log.info("token, username/password, or ssh key was not returned by clouddriver")
                data = K8sData()
            }
        }
        // setting strategy.spinnaker.io/versioned is needed to avoid creating secrets with versioned names e.g. testing1-v000
        return K8sObjectManifest(
            resource.spec.template.apiVersion,
            "Secret",
            mapOf(
                "namespace" to resource.spec.namespace,
                "name" to "${resource.spec.template.metadata["type"]}-${resource.spec.template.data?.account}",
                "annotations" to mapOf("strategy.spinnaker.io/versioned" to "false")
            ),
            null,
            data
        )
    }

    override suspend fun current(resource: Resource<CredentialsResourceSpec>): K8sObjectManifest? {
        log.debug("resource received: $resource spec: ${resource.spec}")
        try {
            val res = cloudDriverK8sService.getK8sResource(
                resource.serviceAccount,
                resource.spec.locations.account,
                resource.spec.namespace,
                "secret ${resource.spec.template.metadata["type"]}-${resource.spec.template.data?.account}"
            )
            log.debug("response from clouddriver: $res manifest: ${res.manifest}")
            return res.manifest
        } catch (e: HttpException) {
            if (e.code() == 404) {
                logger.info("resource ${resource.id} not found")
                return null
            } else {
                throw e
            }
        }
    }

    override suspend fun getK8sResource(r: Resource<CredentialsResourceSpec>): K8sObjectManifest? {
        return cloudDriverK8sService.getK8sResource(r)
    }

    override suspend fun actuationInProgress(resource: Resource<CredentialsResourceSpec>): Boolean =
        resource.spec.template.data?.account.let {
            log.debug(resource.toString())
            val a =
                orcaService.getCorrelatedExecutions("${resource.spec.template.metadata["type"]}-${resource.spec.template.data?.account}")
            log.debug("actuation in progress? ${a.isNotEmpty()}: $a")
            a.isNotEmpty()
        }
}
