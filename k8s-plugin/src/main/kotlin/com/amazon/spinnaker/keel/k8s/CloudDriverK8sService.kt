package com.amazon.spinnaker.keel.k8s

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface CloudDriverK8sService {
    @GET("/manifests/{account}/{location}/{resource}")
    suspend fun getK8sResource(
        @Header("X-SPINNAKER-ACCOUNTS") acc: String,
        @Path("account") account: String,
        @Path("location") location: String,
        @Path("resource") resource: String
    ): K8sResourceModel
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
            );

    override suspend fun getK8sResource(acc: String, account: String, location: String, resource: String): K8sResourceModel {
        return client.getK8sResource(acc, account, location, resource)
    }
}

