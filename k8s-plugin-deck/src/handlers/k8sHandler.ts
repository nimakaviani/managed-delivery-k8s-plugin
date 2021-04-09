import { IManagedDeliveryPlugin, IResourceKindConfig } from '@spinnaker/core';
import { IManagedResourceSummary } from '@spinnaker/core/lib/domain';
import { IconNames } from '@spinnaker/presentation';


class k8sResourceHandler implements IResourceKindConfig {
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

export class k8sManagedDeliveryPlugin implements IManagedDeliveryPlugin {
    resources: IResourceKindConfig[] = [new k8sResourceHandler()]
}