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

import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitArtifactSupplier
import com.amazon.spinnaker.keel.k8s.model.GitRepoArtifact
import com.amazon.spinnaker.keel.k8s.model.GitVersion
import com.amazon.spinnaker.keel.k8s.service.IgorArtifactServiceSupplier
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.*

internal class GitArtifactSupplierTest : JUnit5Minutests {
    private val yamlMapper = configuredYamlMapper()
    private val igorArtifactService = mockk<IgorArtifactServiceSupplier>()
    private val gitVersion1 = GitVersion(
        "testRepo",
        "testProject",
        "igor",
        "1.2.3",
        "123",
        "gitHub",
        "https://repo.url",
        "https://repo.url/commit/123",
        "some date",
        "author1",
        "message1",
        "email@email.email"
    )
    private val gitVersion2 = gitVersion1.copy(version = "1.2.4", commitId = "1234")
    private val gitVersion3 = gitVersion1.copy(version = "1.2.5", commitId = "12345")

    private val invalidGitVersion1 = gitVersion1.copy(version = "v1.2.5")
    private val invalidGitVersion2 = gitVersion1.copy(version = "v.1.2.6")

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
    environments: []
    """.trimIndent()

    fun tests() = rootContext<GitArtifactSupplier> {
        fixture {
            GitArtifactSupplier(
                mockk(),
                mockk(),
                igorArtifactService
            )
        }

        yamlMapper.registerSubtypes(NamedType(GitRepoArtifact::class.java, "git"))
        val deliveryConfig = yamlMapper.readValue(deliveryConfigYaml, DeliveryConfig::class.java)
        val artifact = deliveryConfig.artifacts.first() as GitRepoArtifact

        context("igor plugin is working") {
            before {
                coEvery {
                    igorArtifactService.getGitVersion(artifact.gitType, artifact.project, artifact.repoName, "1.2.3")
                } returns gitVersion1
                coEvery {
                    igorArtifactService.getGitVersions(artifact.gitType, artifact.project, artifact.repoName)
                } returns listOf(
                    gitVersion1,
                    gitVersion2,
                    gitVersion3
                )
            }

            test("not a GitRepoArtifact") {
                val result = getArtifactByVersion(DockerArtifact("", "", "", mockk()), "1.2.3")
                expectThat(result).isNull()
            }

            test("correct info returned") {
                val result = getArtifactByVersion(artifact, "1.2.3")
                expectThat(result).isNotNull()
                expectThat(result?.gitMetadata).isNotNull()
                val gitMetadata = result!!.gitMetadata!!
                with(gitMetadata) {
                    expectThat(commit).isEqualTo("123")
                    expectThat(author).isEqualTo("author1")
                    expectThat(commitInfo!!.sha).isEqualTo("123")
                    expectThat(commitInfo!!.link).isEqualTo("https://repo.url/commit/123")
                    expectThat(commitInfo!!.message).isEqualTo("message1")
                    expectThat(repo!!.link).isEqualTo("https://repo.url")
                    expectThat(repo!!.name).isEqualTo("testRepo")
                }
            }

            test("process artifact") {
                val result = getArtifactByVersion(artifact, "1.2.3")
                expectThat(shouldProcessArtifact(result!!)).isTrue()
            }

            test("should not process artifact") {
                expectThat(
                    shouldProcessArtifact(
                        PublishedArtifact("", "", "", "1.2.3")
                    )
                ).isFalse()
                expectThat(
                    shouldProcessArtifact(
                        PublishedArtifact("", "", "123", "1.2.3")
                    )
                ).isFalse()
                expectThat(
                    shouldProcessArtifact(
                        PublishedArtifact("", "GIT", "123", "1.2.3", metadata = emptyMap())
                    )
                ).isFalse()
                expectThat(
                    shouldProcessArtifact(
                        PublishedArtifact("", "GIT", "123", "1.2.3", metadata = mapOf("not" to "relevant"))
                    )
                ).isTrue()
            }

            test("should return latest artifact") {
                val result = getLatestArtifact(deliveryConfig, artifact)
                expectThat(result!!.version).isEqualTo(gitVersion3.version)
            }

            test("should return two latest versions of artifacts") {
                val result = getLatestArtifacts(deliveryConfig, artifact, 2)
                expectThat(result.size).isEqualTo(2)
                expectThat(result.first().version).isEqualTo(gitVersion3.version)
                expectThat(result.last().version).isEqualTo(gitVersion2.version)
            }
        }
    }
}