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

package com.amazon.spinnaker.keel.k8s.service

import com.amazon.spinnaker.keel.k8s.K8sResourceModel
import com.amazon.spinnaker.keel.k8s.model.ClouddriverDockerImage
import com.amazon.spinnaker.keel.k8s.model.GitRepoAccountDetails
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.clouddriver.model.DockerImage
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudDriverK8sService {
    @GET("/manifests/{account}/{namespace}/{resource}")
    suspend fun getK8sResource(
        @Header("X-SPINNAKER-USER") acc: String,
        @Path("account") account: String,
        @Path("namespace") namespace: String,
        @Path("resource") resource: String
    ): K8sResourceModel

    @GET("/credentialsDetails/gitRepo/{account}")
    suspend fun getCredentialsDetails(
        @Header("X-SPINNAKER-USER") acc: String,
        @Path("account") account: String
    ): GitRepoAccountDetails

    @GET("/dockerRegistry/images/find")
    suspend fun findDockerImages(
        @Query("account") account: String? = null,
        @Query("repository") repository: String? = null,
        @Query("tag") tag: String? = null,
        @Query("q") q: String? = null,
        @Query("includeDetails") includeDetails: Boolean? = null,
        @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT
    ): List<ClouddriverDockerImage>
}

class CloudDriverK8sServiceSupplier(
        private val clouddriverEndpoint: HttpUrl,
        private val objectMapper: ObjectMapper,
        private val clientProvider: OkHttpClientProvider
       ) : CloudDriverK8sService {

    private val client = Retrofit.Builder()
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .baseUrl(clouddriverEndpoint)
            .client(clientProvider.getClient(DefaultServiceEndpoint("clouddriver", clouddriverEndpoint.toString())))
            .build()
            .create(
                    CloudDriverK8sService::class.java
            )

    override suspend fun getK8sResource(acc: String, account: String, namespace: String, resource: String): K8sResourceModel {
        return client.getK8sResource(acc, account, namespace, resource)
    }

    override suspend fun getCredentialsDetails(acc: String, account: String): GitRepoAccountDetails {
        return client.getCredentialsDetails(acc, account)
    }

    override suspend fun findDockerImages(
        account: String?,
        repository: String?,
        tag: String?,
        q: String?,
        includeDetails: Boolean?,
        user: String
    ): List<ClouddriverDockerImage> {
        return client.findDockerImages(account, repository, tag, q, includeDetails, user)
    }
}

