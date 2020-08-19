package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.support.BeanDefinitionRegistry

class ManagedDeliveryK8sPlugin(wrapper: PluginWrapper) : PrivilegedSpringPlugin(wrapper) {

    override fun start() {
        println("starting ManagedDelivery k8s plugin.")
    }

    override fun stop() {
        println("stopping ManagedDelivery k8s plugin.")
    }

    override fun registerBeanDefinitions(registry: BeanDefinitionRegistry?) {
        listOf(
                beanDefinitionFor(CloudDriverK8sServiceSupplier::class.java),
                beanDefinitionFor(K8sResourceHandler::class.java),
                beanDefinitionFor(K8sResolver::class.java)
        ).forEach {
            registerBean(it, registry)
        }
    }


}

