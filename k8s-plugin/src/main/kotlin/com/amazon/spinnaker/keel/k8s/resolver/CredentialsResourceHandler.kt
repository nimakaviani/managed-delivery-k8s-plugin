package com.amazon.spinnaker.keel.k8s.resolver

import com.amazon.spinnaker.keel.k8s.CREDENTIALS_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
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
        val cred = cloudDriverK8sService.getCredentialsDetails(resource.serviceAccount, resource.spec.account)
        val data: MutableMap<String, String> = mutableMapOf()
        // Priority: token > password > ssh key
        when {
            cred.token.isNotBlank() -> {
                log.debug("populating token with username in manifest")
                data.putAll(
                    mapOf(
                        "username" to encoder.encodeToString(cred.token.toByteArray()),
                        "password" to ""
                    )
                )
            }
            cred.username.isNotBlank() -> {
                log.debug("populating username and password in manifest")
                data.putAll(
                    mapOf(
                        "username" to encoder.encodeToString(cred.username.toByteArray()),
                        "password" to encoder.encodeToString(cred.password.toByteArray())
                    )
                )
            }
            cred.sshPrivateKey.isNotBlank() -> {
                log.debug("populating private key in manifest")
                data.putAll(mapOf("identity" to encoder.encodeToString(cred.sshPrivateKey.toByteArray())))
            }
        }
        // setting strategy.spinnaker.io/versioned is needed to avoid creating secrets with versioned names e.g. testing1-v000
        return K8sObjectManifest(
            "v1",
            "Secret",
            mapOf(
                "namespace" to resource.spec.namespace,
                "name" to resource.spec.name,
                "annotations" to mapOf("strategy.spinnaker.io/versioned" to "false")
            ),
            null,
            data as MutableMap<String, Any>?
        )
    }

    override suspend fun current(resource: Resource<CredentialsResourceSpec>): K8sObjectManifest? {
        log.debug("resource received: $resource spec: ${resource.spec}")
        try {
            val res = cloudDriverK8sService.getK8sResource(
                resource.serviceAccount,
                resource.spec.locations.account,
                resource.spec.namespace,
                "secret ${resource.spec.name}"
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
        resource.spec.name.let {
            log.debug(resource.toString())
            val a = orcaService.getCorrelatedExecutions(it)
            log.debug("actuation in progress? ${a.isNotEmpty()}: $a")
            a.isNotEmpty()
        }
}
