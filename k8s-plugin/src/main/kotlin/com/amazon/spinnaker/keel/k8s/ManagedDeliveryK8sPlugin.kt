// Copyright 2021 Amazon.com, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
                beanDefinitionFor(GitArtifactSupplier::class.java),
                beanDefinitionFor(KustomizeResourceHandler::class.java)
        ).forEach {
            registerBean(it, registry)
        }
    }


}

