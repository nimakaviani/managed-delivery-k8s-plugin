package com.amazon.spinnaker.keel.k8s.exception

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import java.lang.Exception

class NoDigestFound(repository: String, tag: String) :
    ResourceCurrentlyUnresolvable("No digest found for docker image $repository:$tag in any registry")

class RegistryNotFound(account: String) :
    IntegrationException("Unable to find docker registry for titus account $account")

class DuplicateReference(reference: String) :
    IntegrationException("there are duplicate containers with reference $reference")

class NotLinked(reference: String) :
    IntegrationException("contianer with reference $reference is not linked in resource. " +
            "resource image should have the same value as the container image for references to be linked")
class CouldNotRetrieveCredentials(accountName: String, e :Throwable? = null) :
    ResourceCurrentlyUnresolvable("could not retrieve account named $accountName in clouddriver", e)

class MisconfiguredObjectException(name: String, value: String, expected: String, e: Throwable? = null):
    ResourceCurrentlyUnresolvable(" $name is configured to $value where it should be $expected", e)

class CredResourceTypeMissing(msg: String, e: Throwable? = null):
    ResourceCurrentlyUnresolvable(msg)

class ResourceNotReady(resource: Resource<ResourceSpec>, e: Throwable? = null):
        Exception("${resource.spec.displayName} is not healthy" )