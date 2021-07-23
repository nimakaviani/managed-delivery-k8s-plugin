/**
 * Copyright 2021 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { IManagedDeliveryPlugin, IResourceKindConfig } from '@spinnaker/core';
import { IManagedResourceSummary } from '@spinnaker/core/lib/domain';
import { IconNames } from '@spinnaker/presentation';


class K8sCredsHandler implements IResourceKindConfig {
    kind = "k8s/credential"
    iconName: IconNames = "spMenuSecurityGroups"
    experimentalDisplayLink = this.displayLink()

    public displayLink(): ((resource: IManagedResourceSummary) => string) {
        return function (resource: IManagedResourceSummary) {
            const path = `#/applications/${resource.moniker?.app}/securityGroups`
            const params = `?acct=${resource.locations?.account}`
            return `${path}${params}`
        }
    }
}

class K8sResourceHandler implements IResourceKindConfig {
    kind = "k8s/resource"
    iconName: IconNames = "spMenuK8s"
    experimentalDisplayLink = this.displayLink()

    public displayLink(): ((resource: IManagedResourceSummary) => string) {
        return function (resource: IManagedResourceSummary) {
            const path = `#/applications/${resource.moniker?.app}/loadBalancers`
            const params = `?acct=${resource.locations?.account}`
            return `${path}${params}`
        }
    }
}

class K8sHelmHandler implements IResourceKindConfig {
    kind = "k8s/helm"
    iconName: IconNames = "spMenuK8s"
    experimentalDisplayLink = this.displayLink()

    public displayLink(): ((resource: IManagedResourceSummary) => string) {
        return function (resource: IManagedResourceSummary) {
            const path = `#/applications/${resource.moniker?.app}/loadBalancers`
            const params = `?acct=${resource.locations?.account}`
            return `${path}${params}`
        }
    }
}

class K8sKustomizeHandler implements IResourceKindConfig {
    kind = "k8s/kustomize"
    iconName: IconNames = "spMenuK8s"
    experimentalDisplayLink = this.displayLink()

    public displayLink(): ((resource: IManagedResourceSummary) => string) {
        return function (resource: IManagedResourceSummary) {
            const path = `#/applications/${resource.moniker?.app}/loadBalancers`
            const params = `?acct=${resource.locations?.account}`
            return `${path}${params}`
        }
    }
}

export class k8sManagedDeliveryPlugin implements IManagedDeliveryPlugin {
    resources: IResourceKindConfig[] = [new K8sResourceHandler(), new K8sCredsHandler(), new K8sHelmHandler(), new K8sKustomizeHandler()]
}