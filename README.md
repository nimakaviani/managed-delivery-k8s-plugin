# Managed Delivery K8s Plugin

## Requirements

### Version Compatibility
| Plugin      |   Keel   |
|:----------- | :--------|
|  <= 0.0.6   |  0.191.1 |
|  0.0.7 <=   |  0.194.1 |

It is also recommended to use Spinnaker Release >  1.25.2 for the most recent
version of Spinnaker components. For tracking of Docker artifacts and integration with CI (i.e. Jenkins),
you can also follow instructions on deploying the [CI Build Plugin](https://github.com/nimakaviani/ci-build-plugin).

For better experience, use the following images for respective microservices (the list updates preiodically):
* Deck: [nimak/spinnaker-deck:0.0.5](https://hub.docker.com/layers/nimak/spinnaker-deck/0.0.5/images/sha256-eab8f3ba56f756dd120db17af6a2910a0e541ff2cc9921671794a2a6208bd626?context=explore)

## Configuration
Managed Delivery Kubernetes plugin extends Keel, Clouddriver, and Deck microservices. For Deck, add
the following snippet to your `gate.yml` profile and enable `deck-proxy` as shown below:

```yaml
spinnaker:
  extensibility:
    plugins-root-path: /opt/gate/plugins
    deck-proxy:
      enabled: true
      plugins:
        aws.ManagedDeliveryK8sPlugin:
          enabled: true
          version: <<plugin release version>>
    repositories:
      awsManagedDeliveryK8sPluginRepo:
        url: https://raw.githubusercontent.com/nimakaviani/managed-delivery-k8s-plugin/master/plugins.json
```

For Keel and Clouddriver, add the following to `clouddriver.yml` and `keel.yml` in the necessary [profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles) to load the plugin.
```yaml
spinnaker:
    extensibility:
      plugins:
        aws.ManagedDeliveryK8sPlugin:
          enabled: true
          version: <<plugin release version>>
      repositories:
        awsManagedDeliveryK8sPluginRepo:
          id: awsManagedDeliveryK8sPluginRepo
          url: https://raw.githubusercontent.com/nimakaviani/managed-delivery-k8s-plugin/master/plugins.json
```
## Delivery Config Manifest

By defining the _Delivery Config Manifest_, you can control the deployment and progression of resources to different
environments (e.g. staging or production), and between environments via constraints.
The skeleton for a delivery config manifest in Spinnaker's managed delivery looks like the following:

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

While the above snippet shows the skeleton for a delivery config manifest,
we skip details on different parts of the manifest here.
More details on the above can be found on the corresponding
[Spinnaker Documentation](https://spinnaker.io/guides/user/managed-delivery/getting-started/) page.

## Deploying to Kubernetes

Support for Kubernetes deployments in the plugin is split into several parts as listed below (expand for details):

<details>
<summary>Deploying Vanilla Kubernetes Resources</summary>

The support for vanilla Kubernetes resources is enabled by having the plugin introduce the new
resource type `k8s/resource@v1` for processing of Kubernetes resources in a delivery config manifest.
The structure of the Kubernetes resource looks like the following:

```yaml
resources:
- kind: k8s/resource@v1 # the versioned vanilla Kubernetes resource
  spec:
    metadata:
      application: # The Spinnaker application name this resource belongs to
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
already configured in your _CloudDriver_ service, the environment definition in your delivery
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
resources by adding more items to the list.
</details>

<details>
<summary>Deploying and Tracking Container Artifacts</summary>

One biggest advantage of Spinnaker's managed delivery is its ability to track artifacts and enforce
rollouts to resources it manages when artifacts change.

If you want to use this plugin to manage rollout of artifacts to Kubernetes, first _CloudDriver_ needs to
be configured to know about these Docker repositories.

**IMPORTANT**: _The Managed Delivery K8s plugin currently only supports one `account` name to be
associated with a resource. In order for the container registry account to be used in combination with the
Kubernetes account (hence, two accounts for a resource), conventionally the container registry account should be named as
follows `[K8-ACCOUNT-NAME]-registry`, where `[K8-ACCOUNT-NAME]` should be identical to the name used for the
Kubernetes account._

```yaml
dockerRegistry:
accounts:
- address: https://index.docker.io # example registry
  name: "[K8s-ACCOUNT-NAME]-registry"
  repositories:
  - example/service
```

To have managed delivery track artifacts, you first introduce them under the delivery config:

```yaml
artifacts:
- name: example/service
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
      reference: my-docker-artifact # indicates the use of artifact in the resource
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

The same `reference` name is used for the artifact under `container.reference` in the Kubernetes
resource `spec`, and also in place of the `image` name for the respective Kubernetes resource. This
enabled the plugin to know exactly which artifact should be use with which resource and where, particularly
where a given resource can deploy multiple artifacts (e.g. for Kubernetes deployments with sidecars or
init containers).

Multiple artifacts can be referenced in a given Kubernetes resource by listing all the artifact references in
the `spec` and then referring to those references in the corresponding resource `image` reference:

```yaml
resources:
- kind: k8s/resource@v1
  spec:
    container:
      references:
      - my-docker-artifact1
      - my-docker-artifact2
```

</details>

<details>
<summary>Deploying HELM and Kustomzie charts</summary>

The plugin relies on [Flux2](https://github.com/fluxcd/flux2) for deployment of HELM and Kustomize resources.
This relieves the plugin from having to deal with the heavy lifting of managing changes to HELM charts
or Kustomization sources where that can be delegated to flux.

In order to get HELM deployments working, first you need to install [Flux2](https://github.com/fluxcd/flux2)
_helm controller_ and _source controller_ into your cluster, with the following command (assuming that you
have Flux2 CLI already installed):

```bash
flux install \
    --namespace=flux-system \
    --network-policy=false \
    --components=source-controller,helm-controller
```

Once the controllers are installed, adding a HELM repository and a HELM release to a delivery config manifest
is similar to how it is done for Kubernetes resources. The managed delivery resource kind however, needs
to be updated to `k8s/helm@v1` for the `HelmRepository`, indicating deployment of a HELM chart using the plugin.

Below, an example is shown for _Crossplane_.

```yaml
resources:
- kind: k8s/resource@v1
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

---

Similarly, for installing Kustomizations, you first add the required flux controllers:

```bash
flux install \
    --namespace=flux-system \
    --network-policy=false \
    --components=source-controller,kustomize-controller
```

then, add the Git repo and the `k8s/kustomize@v1` resource to the delivery manifest:

```yaml
resources:
  - kind: k8s/resource@v1
    spec:
      metadata:
        application: spinmd
      template:
        apiVersion: source.toolkit.fluxcd.io/v1beta1
        kind: GitRepository
        metadata:
          name: crossflux
        spec:
          interval: 5m
          url: ssh://git@github.com/nimakaviani/crossflux.git
          secretRef:
            name: git-deploy-key
          ref:
            branch: main


  - kind: k8s/kustomize@v1
    spec:
      metadata:
        application: spinmd
      template:
        metadata:
          name: setup
        spec:
          interval: 10m0s
          sourceRef:
            kind: GitRepository
            name: crossflux
          path: ./setup
          prune: true
          validation: client
```
</details>

<details>
<summary>Kubernetes Secrets from Clouddriver Credentials</summary>

**Note:** _This is only supported for Git repositories at the time being_.

To deploy HELM charts or Kustomization resources from private Git repositories, you can instruct 
the plugin to pull credential information from Clouddriver and add them as Kubernetes secrets 
to your cluster. 

To do this, in your Clouddriver config file, you will need to provide configuration information
for your Git repository as follows:

```yaml
artifacts:
  gitrepo:
    enabled: true
    accounts:
    - name: sample-repo
      # you can choose username/password
      username: 
      password: 
      
      ## or supply sshKey related data
      sshPrivateKey:
      sshPrivateKeyFilePath: 
      sshPrivateKeyPassphrase: 
      sshPrivateKeyPassphraseCmd: 
      sshKnownHostsFilePath: 
      sshTrustUnknownHosts: 
```

The plugin extends Clouddriver so Git credentials can be queried for through a REST API endpoint.

In your delivery config manifest, you can then add a `k8s/credential@v1` resource that
provides the reference to the right set of credentials for the plugin to create the Kubernetes secret
from. An exmple would be like the following:

```yaml
...
resources:
- kind: k8s/credential@v1
  spec:
    metadata:
      application: my-app
    template:
      metadata:
        namespace: default
      data:
        account: sample-repo
        type: git
```

where the value for `account:` corresponds to the name of the account in your Clouddriver config and 
the `type` of the credential is set to `git`. This in turn will create a secret named `git-sample-repo` 
(prepending the credential type to the account name)
in the `default` namespace, which can be used in your Flux specification of HELM or Kustomize resources.
</details>

## Build and Test

### Build and Install Locally
run `./gradlew releaseBundle` and copy the created zip file to
`/opt/plugin/keel` or your plugin folder of choice. Make sure that the folder is
writable for the plugin to be unzipped in. Enable the plugin by copying the snippet below to your `keel.yml` config file:

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

### Create Test Releases

Under the `hack/` folder, you will find a script that allows you to create a release of 
the plugin and push it to an Amazon S3 repository which you can then use for test purposes.

For the `hack/build.sh` script to work, in addition to your gradle and java installations, 
you need to have the AWS CLI and `jq` installed. 
The `hack/build.sh` script assumes you already have a s3 bucket (default `md-k8s-plugin-bucket`) 
in a given region (default `s3-us-west-2`) that is accessible via your spinnaker microservices.

Running the script, the plugin and the metadata `plugin.json` files are pushed to the 
bucket and then you can use the referenced artifact in your respective microservics configuration 
to test the plugin in deployment.

```yaml
spinnaker:
  extensibility:
    plugins:
      aws.ManagedDeliveryK8sPlugin:
        id: aws.ManagedDeliveryK8sPlugin
        enabled: true
        version: <<plugin version>>
    repositories:
      awsManagedDelvieryK8sPluginRepo:
        id: awsManagedDelvieryK8sPluginRepo
        url: https://$BUCKET.$REGION.amazonaws.com/plugins.json
```


