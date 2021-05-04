package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object HelmResourceSpecTest : JUnit5Minutests {

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
                        |chart:
                        |  reference: something
                        |locations:
                        |  account: my-k8s-west-account
                        |  regions: []
                        |metadata:
                        |  application: test
                        |template:
                        |  apiVersion: source.toolkit.fluxcd.io/v1beta1
                        |  kind: HelmRepository
                        |  metadata:
                        |      name: crossplane-master
                        |  spec:
                        |      interval: 5m
                        |      url: https://charts.crossplane.io/master
                    """.trimMargin()
                )
            }

            derivedContext<HelmResourceSpec>("when deserialized") {
                deriveFixture {
                    mapper.readValue(yaml)
                }

                test("can be deserialized to a Helm object") {
                    expectThat(this)
                        .get { template.spec?.get("interval") }.isEqualTo("5m")
                }

                test("uses default namespace for Helm resource when namespace missing") {
                    expectThat(this)
                        .get { template.namespace() }.isEqualTo("default")
                }

                test("stores correct application metadata from the spec") {
                    expectThat(this)
                        .get { metadata["application"] }.isEqualTo("test")
                }

                test("has chart information properly stored") {
                    expectThat(this)
                        .get { chart?.reference }.isEqualTo("something")
                }
            }
        }
    }
}