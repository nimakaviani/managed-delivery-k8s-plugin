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
        client =  RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(baseUrl))
            .setRequestInterceptor {
                it.addHeader("Authorization", "token $accessToken")
            }
            .setClient(OkClient())
            .setConverter(JacksonConverter())
            .setLog(Slf4jRetrofitLogger(GitHubService::class.java))
            .build()
            .create(GitHubService::class.java)


//            .addConverterFactory(JacksonConverterFactory.create())
//            .baseUrl(baseUrl)
//            .client(OkHttpClient.Builder()
//                .addInterceptor { chain ->
//                    val request = chain.request().newBuilder().addHeader("Authorization", "token $accessToken").build()
//                    chain.proceed(request)
//                }
//                .build())
//            .build()
//            .create(GitHubService::class.java)
    }
}

open class GitHubAccounts(pluginConfigurationProperties: PluginConfigurationProperties) {
    lateinit var accounts: MutableList<GitHubAccount>

    init {
        pluginConfigurationProperties.accounts.forEach{
            val gitHubAccounts = mutableListOf<GitHubAccount>()
            if (it.type.toLowerCase() == "github") {
                gitHubAccounts.add(GitHubAccount(name = it.name, project = it.project))
            }
            accounts = gitHubAccounts
        }
    }
}
