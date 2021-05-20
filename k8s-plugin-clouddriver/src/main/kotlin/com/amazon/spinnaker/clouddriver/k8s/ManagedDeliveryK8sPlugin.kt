package com.amazon.spinnaker.clouddriver.k8s

import com.amazon.spinnaker.clouddriver.k8s.controller.CredentialsDetails
import com.amazon.spinnaker.clouddriver.k8s.services.GitRepoCredentials
import com.netflix.spinnaker.kork.plugins.api.spring.SpringLoaderPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.BeanDefinitionRegistry


class ManagedDeliveryK8sPlugin(wrapper: PluginWrapper) : SpringLoaderPlugin(wrapper) {
    override fun start() {
        log.info("starting ManagedDelivery k8s plugin.")
    }

    override fun stop() {
        log.info("stopping ManagedDelivery k8s plugin.")
    }

    override fun getPackagesToScan(): List<String> {
        return listOf(
            "com.amazon.spinnaker.clouddriver.k8s"
        )
    }

    override fun registerBeanDefinitions(registry: BeanDefinitionRegistry?) {
        listOf(
            beanDefinitionFor(GitRepoCredentials::class.java),
            beanDefinitionFor(CredentialsDetails::class.java),
        ).forEach {
            registerBean(it, registry)
        }
    }
}