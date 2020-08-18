package com.amazon.spinnaker.keel.k8s

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import okhttp3.HttpUrl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.http.GET
import retrofit.http.Header
import retrofit.http.Path
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.function.Supplier

interface CloudDriverK8sService {
    @GET("/manifests/{account}/{location}/{resource}")
    suspend fun getK8sResource(
        @Header("X-SPINNAKER-ACCOUNTS") acc: String,
        @Path("account") account: String,
        @Path("location") location: String,
        @Path("resource") resource: String
    ): K8sResourceModel
}

class CloudDriverK8sServiceSupplier(){
    companion object : Supplier<CloudDriverK8sService> {
        @Autowired lateinit var clouddriverEndpoint: HttpUrl
        @Autowired lateinit var objectMapper: ObjectMapper
        @Autowired lateinit var clientProvider: OkHttpClientProvider

        override fun get(): CloudDriverK8sService =
                Retrofit.Builder()
                        .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                        .baseUrl(clouddriverEndpoint)
                        .client(clientProvider.getClient(DefaultServiceEndpoint("clouddriver", clouddriverEndpoint.toString())))
                        .build()
                        .create(
                                CloudDriverK8sService::class.java
                        );
    }
}

