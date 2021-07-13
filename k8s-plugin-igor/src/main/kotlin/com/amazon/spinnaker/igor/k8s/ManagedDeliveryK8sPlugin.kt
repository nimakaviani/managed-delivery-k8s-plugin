package com.amazon.spinnaker.igor.k8s

import com.amazon.spinnaker.igor.k8s.cache.GitCache
import com.amazon.spinnaker.igor.k8s.config.GitHubAccounts
import com.amazon.spinnaker.igor.k8s.config.GitHubRestClient
import com.amazon.spinnaker.igor.k8s.config.PluginConfigurationProperties
import com.amazon.spinnaker.igor.k8s.controller.GitVersionController
import com.amazon.spinnaker.igor.k8s.monitor.GitHubMonitor
import com.amazon.spinnaker.igor.k8s.service.GitControllerService
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.BeanDefinitionRegistry

//    Must use PrivilegedSpringPlugin because plugin initiation is done too late in SpringLoaderPlugin
class ManagedDeliveryK8sPlugin(wrapper: PluginWrapper) : PrivilegedSpringPlugin(wrapper) {
    override fun start() {
        log.info("starting ManagedDelivery k8s plugin.")
    }

    override fun stop() {
        log.info("stopping ManagedDelivery k8s plugin.")
    }

    override fun registerBeanDefinitions(registry: BeanDefinitionRegistry?) {
        listOf(
            beanDefinitionFor(GitHubRestClient::class.java),
            beanDefinitionFor(GitHubAccounts::class.java),
            beanDefinitionFor(GitHubMonitor::class.java),
            beanDefinitionFor(GitControllerService::class.java),
            beanDefinitionFor(GitVersionController::class.java),
            beanDefinitionFor(PluginConfigurationProperties::class.java),
            beanDefinitionFor(GitCache::class.java),
        ).forEach {
            registerBean(it, registry)
        }
    }
}