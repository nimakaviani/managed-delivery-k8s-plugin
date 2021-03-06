package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.model.CredentialsResourceSpec
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class CredentialsResourceSpecTest : JUnit5Minutests {

    data class Fixture(
        val mapper: ObjectMapper = configuredYamlMapper(),
        val yaml: String
    )

    fun tests() = rootContext<Fixture> {
        context("a simple Helm resource definition in yaml") {
            fixture {
                Fixture(
                    yaml = """
                        |---
                        |locations:
                        |  account: my-k8s-west-account
                        |  regions: []
                        |metadata:
                        |  application: fnord
                        |template:
                        |  metadata:
                        |    namespace: default
                        |  data:
                        |    account: something
                        |    type: git
                    """.trimMargin()
                )
            }

            derivedContext<CredentialsResourceSpec>("when deserialized") {
                deriveFixture {
                    mapper.readValue(yaml)
                }

                test("name matches the specification") {
                    expectThat(this).get { id }.isEqualTo("my-k8s-west-account-default-secret-git-something")
                }

                test("name matches the specification") {
                    expectThat(this).get { displayName }.isEqualTo("my-k8s-west-account  default::Secret  (git-something)")
                }
            }
        }
    }
}