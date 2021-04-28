package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.CREDENTIALS_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.k8s.K8sObjectManifest
import com.amazon.spinnaker.keel.k8s.K8sResourceModel
import com.amazon.spinnaker.keel.k8s.K8sSpec
import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
import com.amazon.spinnaker.keel.k8s.model.GitRepoAccountDetails
import com.amazon.spinnaker.keel.k8s.resolver.CredentialsResourceHandler
import com.amazon.spinnaker.keel.k8s.resolver.K8sResolver
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sService
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.OrcaTaskLauncher
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import retrofit2.HttpException
import retrofit2.Response
import strikt.api.expectThat
import strikt.assertions.*
import java.util.*

@Suppress("UNCHECKED_CAST")
class CredentialsHandlerTest : JUnit5Minutests {
    private val cloudDriverK8sService = mockk<CloudDriverK8sService>()
    private val orcaService = mockk<OrcaService>()
    private val publisher: EventPublisher = mockk(relaxUnitFun = true)
    private val repository = mockk<KeelRepository>()
    private val springEnv: Environment = mockk(relaxUnitFun = true)
    private val taskLauncher = OrcaTaskLauncher(
        orcaService,
        repository,
        publisher,
        springEnv
    )
    private val resolvers: List<Resolver<*>> = listOf(
        K8sResolver()
    )
    private val decoder: Base64.Decoder = Base64.getMimeDecoder()
    private val yamlMapper = configuredYamlMapper()
    private val yaml = """
        |---
        |locations:
        |  account: my-k8s-west-account
        |  regions: []
        |metadata:
        |  application: test
        |namespace: test-ns
        |account: test-git-repo
    """.trimMargin()
    private val spec = yamlMapper.readValue(yaml, CredentialsResourceSpec::class.java)
    private val resource = resource(
        kind = CREDENTIALS_RESOURCE_SPEC_V1.kind,
        spec = spec
    )
    private val privateKey = """
        |-----BEGIN OPENSSH PRIVATE KEY-----
        |b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
        |NhAAAAAwEAAQAAAYEA2HUVhOCM5Xni9aITmYYatzpkUYcu57Npd5ugaY7+nhOM1OgvTjYG
        |ckGSRmO645oWUiQauTZ1gzkpre9WtIyUUUC52ZrTLCBzM7N6TdVcaQ1/UvYRGBmoviV43X
        |AgMEBQ==
        |-----END OPENSSH PRIVATE KEY-----
        """.trimMargin()

    fun tests() = rootContext<CredentialsResourceHandler> {
        fixture {
            CredentialsResourceHandler(
                cloudDriverK8sService,
                taskLauncher,
                publisher,
                orcaService,
                resolvers
            )
        }

        context("credentials exists") {
            coEvery { cloudDriverK8sService.getCredentialsDetails(any(), "test-git-repo") } returnsMany listOf(
                GitRepoAccountDetails(token = "token1"),
                GitRepoAccountDetails(username = "testUser", password = "testPass"),
                GitRepoAccountDetails(sshPrivateKey = privateKey),
                GitRepoAccountDetails(token = "token1", password = "doNotUse"),
                GitRepoAccountDetails(username = "testUser", password = "testPass", sshPrivateKey = privateKey)
            )

            test("should return a kubernetes secret manifest") {
                runBlocking {
                    val result = toResolvedType(resource)
                    val annotations = result.metadata["annotations"] as Map<String, String>
                    expectThat(result.metadata) {
                        hasEntry("name", "test-git-repo")
                        hasEntry("namespace", "test-ns")
                    }
                    expectThat(annotations["strategy.spinnaker.io/versioned"]).isEqualTo("false")
                    expectThat(
                        decoder.decode(result.data?.get("username") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("token1")
                    expectThat(result.data?.get("password")).isEqualTo("")
                }
            }

            test("should return username and password") {
                runBlocking {
                    val result = toResolvedType(resource)
                    expectThat(
                        decoder.decode(result.data?.get("username") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("testUser")
                    expectThat(
                        decoder.decode(result.data?.get("password") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("testPass")
                }
            }

            test("should return sshKey") {
                runBlocking {
                    val result = toResolvedType(resource)
                    expectThat(
                        decoder.decode(result.data?.get("identity") as String).toString(Charsets.UTF_8)
                    ).isEqualTo(privateKey)
                }
            }

            test("should ignore password") {
                runBlocking {
                    val result = toResolvedType(resource)
                    expectThat(
                        decoder.decode(result.data?.get("username") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("token1")
                    expectThat(
                        decoder.decode(result.data?.get("password") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("")
                }
            }

            test("should ignore ssh key") {
                runBlocking {
                    val result = toResolvedType(resource)
                    expectThat(
                        decoder.decode(result.data?.get("username") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("testUser")
                    expectThat(
                        decoder.decode(result.data?.get("password") as String).toString(Charsets.UTF_8)
                    ).isEqualTo("testPass")
                    expectThat(result.data?.get("identity")).isNull()
                }
            }
        }

        context("resource exists") {
            before {
                val manifest = K8sObjectManifest(
                    apiVersion = "v1",
                    kind = "Secret",
                    metadata = mapOf(
                        "name" to "test-git-repo",
                    ),
                    spec = mutableMapOf<String, Any>() as K8sSpec
                )
                clearMocks(cloudDriverK8sService)
                coEvery {
                    cloudDriverK8sService.getK8sResource(
                        any(),
                        "my-k8s-west-account",
                        "test-ns",
                        "secret test-git-repo"
                    )
                } returnsMany listOf(
                    K8sResourceModel("", null, null, null, manifest, null, null, null, null, null),
                )
            }

            test("should return manifest") {
                runBlocking {
                    val result = current(resource)
                    expectThat(result!!.metadata["name"]).isEqualTo("test-git-repo")
                }
            }
        }

        context("resource does not exists") {
            before {
                val notFound: Response<Any> =
                    Response.error(HttpStatus.NOT_FOUND.value(), ResponseBody.create(null, "not found"))
                clearMocks(cloudDriverK8sService)
                coEvery {
                    cloudDriverK8sService.getK8sResource(
                        any(),
                        "my-k8s-west-account",
                        "test-ns",
                        "secret test-git-repo"
                    )
                } throws HttpException(notFound)
            }

            test("should return null") {
                runBlocking {
                    val result = current(resource)
                    expectThat(result).isNull()
                }
            }
        }

        context("actuation is in progress") {
            before {
                coEvery { orcaService.getCorrelatedExecutions(resource.spec.account) } returnsMany listOf(
                    listOf("executionId1"), emptyList()
                )
            }

            test("should return correct status") {
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
