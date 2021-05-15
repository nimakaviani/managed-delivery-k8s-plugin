import { IDeckPlugin } from '@spinnaker/core';
import {k8sManagedDeliveryPlugin} from "./handlers/k8sHandler"
export const plugin: IDeckPlugin = {
    managedDelivery: new k8sManagedDeliveryPlugin
};
