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

package com.amazon.spinnaker.igor.k8s.config

import com.amazon.spinnaker.igor.k8s.model.GitHubAccount
import com.amazon.spinnaker.igor.k8s.service.GitHubService
import com.netflix.spinnaker.igor.config.GitHubProperties
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

@Configuration
@ConditionalOnProperty("github.base-url")
open class GitHubRestClient(gitHubProperties: GitHubProperties) {
    var client: GitHubService

    init {
        val baseUrlField = GitHubProperties::class.java.getDeclaredField("baseUrl")
        val accessTokenField = GitHubProperties::class.java.getDeclaredField("accessToken")
        baseUrlField.isAccessible = true
        accessTokenField.isAccessible = true
        val baseUrl = baseUrlField.get(gitHubProperties) as String
        val accessToken = accessTokenField.get(gitHubProperties) as String
        client = RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(baseUrl))
            .setRequestInterceptor {
                it.addHeader("Authorization", "token $accessToken")
            }
            .setClient(OkClient())
            .setConverter(JacksonConverter())
            .setLog(Slf4jRetrofitLogger(GitHubService::class.java))
            .build()
            .create(GitHubService::class.java)
    }
}

open class GitHubAccounts(pluginConfigurationProperties: PluginConfigurationProperties) {
    var accounts: MutableList<GitHubAccount>

    init {
        val gitHubAccounts = mutableListOf<GitHubAccount>()
        pluginConfigurationProperties.repositories.forEach {
            if (it.type.toLowerCase() == "github") {
                gitHubAccounts.add(GitHubAccount(name = it.name, project = it.project, url = it.url))
            }
        }
        accounts = gitHubAccounts
    }
}
