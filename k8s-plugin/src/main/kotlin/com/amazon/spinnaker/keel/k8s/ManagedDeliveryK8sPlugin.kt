package com.amazon.spinnaker.keel.k8s

import com.amazon.spinnaker.keel.k8s.artifactSupplier.GitArtifactSupplier
import com.amazon.spinnaker.keel.k8s.resolver.*
import com.amazon.spinnaker.keel.k8s.service.CloudDriverK8sServiceSupplier
import com.amazon.spinnaker.keel.k8s.service.IgorArtifactServiceSupplier
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
                beanDefinitionFor(IgorArtifactServiceSupplier::class.java),
                beanDefinitionFor(K8sResourceHandler::class.java),
                beanDefinitionFor(HelmResourceHandler::class.java),
                beanDefinitionFor(K8sResolver::class.java),
                beanDefinitionFor(DockerImageResolver::class.java),
                beanDefinitionFor(CredentialsResourceHandler::class.java),
                beanDefinitionFor(KustomizeResourceHandler::class.java),
                beanDefinitionFor(GitArtifactSupplier::class.java)
        ).forEach {
            registerBean(it, registry)
        }
    }


}

