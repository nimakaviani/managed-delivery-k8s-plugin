# Managed Delivery K8s Plugin

## Configuration
Add the following to `keel.yml` in the necessary [profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles) to load plugin.
```yaml
spinnaker:
    extensibility:
      plugins-root-path: /opt/keel/plugins
      plugins:
        aws.ManagedDeliveryK8sPlugin:
          enabled: true
          version: <<plugin release version>>
      repositories:
        awsManagedDelvieryK8sPluginRepo:
          id: awsManagedDelvieryK8sPluginRepo
          url: https://raw.githubusercontent.com/nimakaviani/managed-delivery-k8s-plugin/master/plugins.json
```
## Delivery Config Manifest

The skeleton for a delivery config manifest in Spinnaker managed delivery looks like the following:

```yaml
name: # delivery config name
application: # spinnaker application name
serviceAccount: # spinnaker service account
artifacts: [] # list of artifacts
environments:
  - name: # environment where the app gets deployed
    locations:
      account: # an account used to deploy resources
      regions: [] # list of regions (if applies) for the application to be deployed to
    resources: [] # list of resources to be deployed
```

More details on the above can be found on the corresponding [Spinnaker Documentation](https://spinnaker.io/guides/user/managed-delivery/getting-started/) page.

## Deploying Kubernetes Resources

Support for delploying Kubernets resources in the plugin is split into two parts:
- Supporting vanilla Kubernetes resources
- Tracking and continuous rollout of containers associated with Kubernetes resources

The remainder of this section walks you through specifying resources and artifacts to your 
managed delivery manifest.

### Deploying Vanilla Kubernetes Resources

The Support for vanilla Kubernetes resources is enabled by having the plugin introduce the new
resource type `k8s/resource@v1` for processing of Kubernetes resources in a delivery configuration.
The structure of the Kubernetes resource looks like the following:

```yaml
resources:
- kind: k8s/resource@v1 # indicating a vanilla Kubernets resource
  spec:
    metadata:
      application: # Spinnaker application the resource belongs to
    template: {} # the vanilla YAML document for a Kubernetes resource      
```

Consider the following as an example of a Kubernetes service:

```yaml
resources:
- kind: k8s/resource@v1
  spec:
    metadata:
      application: my-app
    template:
      apiVersion: v1
      kind: Service
      metadata:
        name: my-service
        namespace: default
        annotations:
          app: hello
      spec:
        type: LoadBalancer
        externalTrafficPolicy: Cluster
        ports:
        - port: 80
          targetPort: 8080
        selector:
          app: hello
```

Assuming that this needs to be deployed to a `test` environment, with a Kubernetes account 
already configured in your Spinnaker _CloudDriver_, the environment definition in your delivery
manifest could be as follows:

```yaml
environments:
  - name: test-env
    locations:
      account: clouddriver-k8s-account
      regions: []
    resources:
    - kind: k8s/resource@v1
      spec:
        metadata:
          application: my-app
        template:
          apiVersion: v1
          kind: Service
          metadata:
            name: my-service
            namespace: default
            annotations:
              app: hello
          spec:
            type: LoadBalancer
            externalTrafficPolicy: Cluster
            ports:
              - port: 80
                targetPort: 8080
            selector:
              app: hello
```

if you need more Kubernetes resources to be deployed to this environment, you can expand the list of 
resources.

### Deploying Kubernetes Resources and Tracking Artifacts

One biggest advantage of Spinnaker's managed delivery is its ability to track artifacts and enforce
rollouts to resources it manages when the artifacts change.

If you want to use this plugin to manage rollout of artifacts to Kubernetes, first _CloudDriver_ needs to
be configured to know about these Docker repositories (see configuration section).

To have managed delivery track artifacts, you first introduce them under the delivery config:

```yaml
artifacts:
- name: nimak/helloworld
  type: docker
  reference: my-docker-artifact
  tagVersionStrategy: semver-tag
```

Then in your Kubernetes resource specification, you bind the artifact to the target resource using the
artifact `reference`:

```yaml
resources:
- kind: k8s/resource@v1
  spec:
    container:
      reference: my-docker-artifact # indicates use of artifact in the resource
    metadata:
      application: spinmd
    template:
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: my-app-deployment
        namespace: default            
      spec:
        replicas: 1
        selector:
          matchLabels:
            app: hello
        template:
          metadata:
            labels:
              app: hello                
          spec:
            containers:
            - name: hello
              image: my-docker-artifact # binds the artifact to the deployment
              ports:
              - containerPort: 8080
```

The same `reference` name is used for the artifact, under `container.reference` in the Kubernetes 
resource `spec`, and also in place of the `image` name for the respective Kubernetes resource. This
enabled the plugin to know exactly which artifact should be use with which resource and where, particularly
where a given resource can deploy multiple artifacts (e.g. for Kubernetes deployments with sidecars or
init containers).

## Deploying HELM charts

The plugin relies on [Flux2](https://github.com/fluxcd/flux2) for deployment of HELM resources.
This relieves the plugin from having to deal with the heavy lifting of managing changes to HELM charts
where that can be delegated to the vibrant flux ecosystem.

In order to get HELM deployments working, first you need to install [Flux2](https://github.com/fluxcd/flux2)
_helm controller_ and _source controller_ into your cluster, with the following command:

```bash
flux install \
    --namespace=flux-system \
    --network-policy=false \
    --components=source-controller,helm-controller
```

Once the controllers are installed, adding a HELM repository and a HELM release to a delivery config manifest
is similar to how it is done for Kubernetes resources. Below, an example is shown for _crossplane_.

```yaml
resources:
- kind: k8s/helm@v1
  spec:
    metadata:
      application: spinmd
    template:
      apiVersion: source.toolkit.fluxcd.io/v1beta1
      kind: HelmRepository
      metadata:
          name: crossplane-master
          namespace: flux-system
      spec:
          interval: 5m
          url: https://charts.crossplane.io/master
- kind: k8s/helm@v1
  spec:
    metadata:
      application: spinmd
    template:
      apiVersion: helm.toolkit.fluxcd.io/v2beta1
      kind: HelmRelease
      metadata:
        name: crossplane
        namespace: flux-system
      spec:
        releaseName: crossplane
        targetNamespace: crossplane-system
        chart:
          spec:
            chart: crossplane
            version: 1.2.0-rc.0.113.gb94884d0
            sourceRef:
              kind: HelmRepository
              name: crossplane-master
              namespace: flux-system
        interval: 1m
        install:
          remediation:
            retries: 3 
```

**Note:** _Tracking of charts on HELM repositories is not yet supported in the plugin_. 

## Build
run `./gradlew releaseBundle` and copy the created zip file to
`/opt/plugin/keel` or your plugin folder of choice. Make sure that the folder is
writable for the plugin to be unzipped in.

## Install

Enable the plugin by copying the snippet below to your `keel.yml` config file:

```yaml
spinnaker:
  extensibility:
    plugins-root-path: /opt/plugin/keel
    plugins:
      aws.ManagedDeliveryK8sPlugin:
        enabled: true
    repositories: {}
    strict-plugin-loading: false
```
