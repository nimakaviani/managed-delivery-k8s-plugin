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