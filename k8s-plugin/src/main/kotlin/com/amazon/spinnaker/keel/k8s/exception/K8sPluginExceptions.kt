package com.amazon.spinnaker.keel.k8s.exception

import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.kork.exceptions.IntegrationException

class NoDigestFound(repository: String, tag: String) :
    ResourceCurrentlyUnresolvable("No digest found for docker image $repository:$tag in any registry")

class RegistryNotFound(account: String) :
    IntegrationException("Unable to find docker registry for titus account $account")

class DuplicateReference(reference: String) :
    IntegrationException("there are duplicate containers with reference $reference")

class NotLinked(reference: String) :
    IntegrationException("contianer with reference $reference is not linked in resource. " +
            "resource image should have the same value as the container image for references to be linked")