// Copyright 2022 Amazon.com, Inc.
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

package com.amazon.spinnaker.igor.k8s

import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.PluginConfigurationProperties
import com.amazon.spinnaker.igor.k8s.model.GitAccount
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class GitHubConfigurationTest {
    var prop: PluginConfigurationProperties = PluginConfigurationProperties()

    @BeforeEach
    fun setupMocks() {
        val repos = mutableListOf(
            GitAccount(
                name = "test1",
                type = "github",
                project = "test-project",
                url = "some-url"
            ),
            GitAccount(
                name = "test2",
                type = "github",
                project = "test-project-1",
                url = "some-url"
            ),
            GitAccount(
                name = "test3",
                type = "gitlab",
                project = "test-project-3",
                url = "some-url"
            )
        )
        prop.repositories = repos
    }

    @Test
    fun `should populate github only`() {
        val subj = GitHubAccounts(this.prop)
        expectThat(subj.accounts.size).isEqualTo(2)
        subj.accounts.forEach{
            expectThat(it.type).isEqualTo("github")
        }
    }

}