package com.amazon.spinnaker.igor.k8s.config

import com.amazon.spinnaker.igor.k8s.service.GitHubService
import com.netflix.spinnaker.igor.config.GitHubConfig
import com.netflix.spinnaker.igor.config.GitHubProperties
import okhttp3.OkHttpClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty("github.base-url")
open class GitHubConfiguration {

    @Bean
    open fun gitHubService(gitHubProperties: GitHubProperties): GitHubService {
        val baseUrlField = GitHubProperties::class.java.getDeclaredField("baseUrl")
        val accessTokenField = GitHubProperties::class.java.getDeclaredField("accessToken")
        baseUrlField.isAccessible = true
        accessTokenField.isAccessible = true
        val baseUrl = baseUrlField.get(gitHubProperties) as String
        val accessToken = accessTokenField.get(gitHubProperties) as String
        return Retrofit.Builder()
            .addConverterFactory(JacksonConverterFactory.create())
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder().addHeader("Authorization", "token $accessToken").build()
                    chain.proceed(request)
                }
                .build())
            .build()
            .create(GitHubService::class.java)
    }
}