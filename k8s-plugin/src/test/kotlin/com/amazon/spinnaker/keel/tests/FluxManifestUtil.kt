package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.resolver.FluxManifestUtil.generateGitRepoManifest
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

internal class FluxManifestUtilTest : JUnit5Minutests {
    val artifact = GitRepoArtifact(
        null,
        "my-ref",
        "testRepo",
        "testProject",
        "testType",
        TagVersionStrategy.INCREASING_TAG,
        "testNamespace",
        "2m",
        "secretRef"
    )

    fun tests() = rootContext {
        context("test") {
            test("return with env") {
                val manifest = generateGitRepoManifest(artifact, "fake-url", "1.0.0", "testEnv")
                expectThat(manifest.namespace()).isEqualTo(artifact.namespace)
                expectThat(manifest.metadata)
                    .hasEntry("name", "${artifact.name}-testEnv")
                    .hasEntry("namespace", artifact.namespace)
                expectThat(manifest.spec as MutableMap<String, Any>)
                    .hasEntry("url", "fake-url")
                    .hasEntry("interval", artifact.interval)
                    .hasEntry("ref", mutableMapOf("tag" to "1.0.0"))
                    .hasEntry("secretRef", mutableMapOf("name" to "secretRef"))
            }

            test("return without env") {
                val manifest = generateGitRepoManifest(artifact, "fake-url", "1.0.0")
                expectThat(manifest.namespace()).isEqualTo(artifact.namespace)
                expectThat(manifest.metadata)
                    .hasEntry("name", "${artifact.name}")
                expectThat(manifest.spec as MutableMap<String, Any>)
                    .hasEntry("ref", mutableMapOf("tag" to "1.0.0"))
            }

            test("return without env") {
                val manifest = generateGitRepoManifest(artifact, "fake-url", null)
                expectThat(manifest.namespace()).isEqualTo(artifact.namespace)
                expectThat(manifest.metadata)
                    .hasEntry("name", "${artifact.name}")
                expectThat((manifest.spec as MutableMap<String, Any>).get("ref")).isNull()
            }
        }
    }
}
