package com.amazon.spinnaker.keel.k8s.exception

import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.kork.exceptions.IntegrationException

class NoDigestFound(repository: String, tag: String) :
    ResourceCurrentlyUnresolvable("No digest found for docker image $repository:$tag in any registry")

class RegistryNotFound(account: String) :
    IntegrationException("Unable to find docker registry for titus account $account")