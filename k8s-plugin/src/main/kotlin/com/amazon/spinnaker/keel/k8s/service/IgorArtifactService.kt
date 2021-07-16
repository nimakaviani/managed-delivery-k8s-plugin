package com.amazon.spinnaker.keel.k8s.service

import com.amazon.spinnaker.keel.k8s.model.GitVersion
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.keel.retrofit.InstrumentedJacksonConverter
import okhttp3.HttpUrl
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

interface IgorArtifactRestService {
    @GET("/git/version/{type}/{project}/{slug}")
    suspend fun getGitVersions(
        @Path("type") type: String,
        @Path("project") project: String,
        @Path("slug") slug: String
    ): List<GitVersion>

    @GET("/git/version/{type}/{project}/{slug}/{version}")
    suspend fun getGitVersion(
        @Path("type") type: String,
        @Path("project") project: String,
        @Path("slug") slug: String,
        @Path("version") version: String
    ): GitVersion
}

class IgorArtifactServiceSupplier(
    igorEndpoint: HttpUrl,
    objectMapper: ObjectMapper,
    clientProvider: OkHttpClientProvider
): IgorArtifactRestService {
    private val client = Retrofit.Builder()
        .addConverterFactory(InstrumentedJacksonConverter.Factory("igor", objectMapper))
        .baseUrl(igorEndpoint)
        .client(clientProvider.getClient(DefaultServiceEndpoint("igor", igorEndpoint.toString())))
        .build()
        .create(IgorArtifactRestService::class.java)

    override suspend fun getGitVersions(type: String, project: String, slug: String): List<GitVersion> {
        return client.getGitVersions(type,project, slug)
    }

    override suspend fun getGitVersion(type: String, project: String, slug: String, version: String): GitVersion {
        return client.getGitVersion(type, project, slug, version)
    }
}