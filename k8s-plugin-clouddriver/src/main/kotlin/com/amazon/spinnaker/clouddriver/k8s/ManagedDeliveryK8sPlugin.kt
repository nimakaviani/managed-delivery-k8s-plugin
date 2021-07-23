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

package com.amazon.spinnaker.clouddriver.k8s

import com.netflix.spinnaker.kork.plugins.api.spring.SpringLoaderPlugin
import org.pf4j.PluginWrapper


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
}