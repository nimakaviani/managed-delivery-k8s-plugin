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

package com.amazon.spinnaker.keel.k8s.exception

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import java.lang.Exception

class NoDigestFound(repository: String, tag: String) :
    ResourceCurrentlyUnresolvable("No digest found for docker image $repository:$tag in any registry")

class DockerImageNotFound(account: String, repository: String, tag: String) :
    ResourceCurrentlyUnresolvable("No Docker image found for $repository:$tag in $account")

class RegistryNotFound(account: String) :
    IntegrationException("Unable to find docker registry for account $account")

class DuplicateReference(reference: String) :
    IntegrationException("there are duplicate containers with reference $reference")

class NotLinked(reference: String) :
    IntegrationException("contianer with reference $reference is not linked in resource. " +
            "resource image should have the same value as the container image for references to be linked")
class CouldNotRetrieveCredentials(accountName: String, e :Throwable? = null) :
    ResourceCurrentlyUnresolvable("could not retrieve account named $accountName in clouddriver", e)

class MisconfiguredObjectException(msg: String, e: Throwable? = null):
    ResourceCurrentlyUnresolvable(msg, e)

class CredResourceTypeMissing(msg: String, e: Throwable? = null):
    ResourceCurrentlyUnresolvable(msg)

class ResourceNotReady(resource: Resource<ResourceSpec>, e: Throwable? = null):
        Exception("${resource.spec.displayName} is not healthy" )
