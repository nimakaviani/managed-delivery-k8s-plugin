package com.amazon.spinnaker.clouddriver.k8s

import com.amazon.spinnaker.clouddriver.k8s.controller.CredentialsDetails
import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.BeanDefinitionRegistry


class ManagedDeliveryK8sPlugin(wrapper: PluginWrapper) : PrivilegedSpringPlugin(wrapper) {
    override fun start() {
        log.info("starting ManagedDelivery k8s plugin.")
    }

    override fun stop() {
        log.info("stopping ManagedDelivery k8s plugin.")
    }
    override fun registerBeanDefinitions(registry: BeanDefinitionRegistry?) {
        listOf(
            beanDefinitionFor(CredentialsDetails::class.java)
        ).forEach {
            registerBean(it, registry)
        }
    }
}