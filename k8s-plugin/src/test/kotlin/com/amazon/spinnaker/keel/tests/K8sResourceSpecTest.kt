package com.amazon.spinnaker.keel.tests

import com.amazon.spinnaker.keel.k8s.model.K8sResourceSpec
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object K8sResourceSpecTests : JUnit5Minutests {

    data class Fixture(
        val mapper: ObjectMapper = configuredYamlMapper(),
        val yaml: String
    )

    fun tests() = rootContext<Fixture> {
        context("a simple K8s resource definition in yaml") {
            fixture {
                Fixture(
                    yaml = """
                        |---
                        |locations:
                        |  account: my-k8s-west-account
                        |  regions: []
                        |metadata:
                        |  application: test
                        |template:
                        |  apiVersion: "apps/v1"
                        |  kind: Deployment
                        |  metadata:
                        |    name: hello-kubernetes
                        |    annotations:
                        |      moniker.spinnaker.io/application: spinmd
                        |  spec:
                        |    replicas: 2
                        |    selector:
                        |      matchLabels:
                        |        app: hello-kubernetes
                        |    template:
                        |      metadata:
                        |        labels:
                        |          app: hello-kubernetes
                        |      spec:
                        |        containers:
                        |        - name: hello-kubernetes
                        |          image: paulbouwer/hello-kubernetes:1.8
                        |          ports:
                        |          - containerPort: 8080
                    """.trimMargin()
                )
            }

            derivedContext<K8sResourceSpec>("when deserialized") {
                deriveFixture {
                    mapper.readValue(yaml)
                }

                test("name matches the specification") {
                    expectThat(this).get { id }.isEqualTo("my-k8s-west-account-default-deployment-hello-kubernetes")
                }

                test("name matches the specification") {
                    expectThat(this).get { displayName }.isEqualTo("my-k8s-west-account-default-deployment-hello-kubernetes")
                }

                test("can be deserialized to a K8s object") {
                    expectThat(this)
                        .get { template.spec?.get("replicas") }.isEqualTo(2)
                }

                test("uses default namespace for k8s resource when namespace missing") {
                    expectThat(this)
                        .get { template.namespace() }.isEqualTo("default")
                }

                test("stores correct application metadata from the spec") {
                    expectThat(this)
                        .get { metadata["application"] }.isEqualTo("test")
                }
            }
        }
    }
}