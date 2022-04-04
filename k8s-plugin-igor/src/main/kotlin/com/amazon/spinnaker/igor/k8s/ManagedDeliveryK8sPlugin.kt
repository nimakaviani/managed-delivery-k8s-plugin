// Copyright 2022 Amazon.com, Inc.
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