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

import com.amazon.spinnaker.keel.k8s.model.HelmResourceSpec
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

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
                        |locations:
                        |  account: my-k8s-west-account
                        |  regions: []
                        |metadata:
                        |  application: fnord
                        |artifactSpec:
                        |  ref: my-git-artifact
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
                        .get { metadata["application"] }.isEqualTo("fnord")
                }

                test("artifact ref stored correctly") {
                    expectThat(this)
                        .get { artifactReference }.isEqualTo("my-git-artifact")
                }
            }
        }
        context("a simple Helm resource definition without artifact ref") {
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

                test("null artifact ref returned") {
                    expectThat(this)
                        .get { artifactReference }.isNull()
                }
            }
        }
    }
}