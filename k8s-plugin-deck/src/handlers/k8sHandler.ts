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