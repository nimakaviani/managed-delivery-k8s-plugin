package com.amazon.spinnaker.keel.k8s

import com.netflix.spinnaker.kork.plugins.api.spring.PrivilegedSpringPlugin
import org.pf4j.PluginWrapper
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition

class ManagedDeliveryK8sPlugin(wrapper: PluginWrapper) : PrivilegedSpringPlugin(wrapper) {

    override fun start() {
        println("starting ManagedDelivery k8s plugin.")
    }

    override fun stop() {
        println("stopping ManagedDelivery k8s plugin.")
    }

    override fun registerBeanDefinitions(registry: BeanDefinitionRegistry?) {

        val serviceImplBeanDef = BeanDefinitionBuilder.genericBeanDefinition(
                CloudDriverK8sService::class.java, CloudDriverK8sServiceSupplier).beanDefinition
        serviceImplBeanDef.setScope(BeanDefinition.SCOPE_SINGLETON)
        serviceImplBeanDef.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
        
        listOf(
                serviceImplBeanDef,
                beanDefinitionFor(K8sResourceHandler::class.java),
                beanDefinitionFor(K8sResolver::class.java)
        ).forEach {
            registerBean(it, registry)
        }
    }


}

