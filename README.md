# Managed Delivery K8s Plugin

## Requirements

### Version Compatibility
| Plugin       |   Keel   |
|:------------ | :--------|
|  <= 0.0.6    |  0.191.1 |
|  0.0.7       |  0.194.1 |
|  0.0.{8,9}   |  0.195.0 |
|  0.0.10      |  0.197.0 |
|  0.0.11      |  0.202.0 |

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

### Supported artifact types

One biggest advantage of Spinnaker's managed delivery is its ability to track artifacts and enforce
rollouts to resources it manages when artifacts change.

<details>
<summary>Deploying and Tracking Container Artifacts</summary>

If you want to use this plugin to manage rollout of Docker container image artifacts to Kubernetes, first _CloudDriver_ needs to
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
<summary>Deploying and Tracking Git Artifacts</summary>

Git repositories can be treated as Keel artifacts. This functionality is primarily meant to be used by the Kustomize (k8s/kustomize@v1)
and Helm (k8s/helm@v1) resource types. 

### Igor plugin configuration
An example configuration is shown below. New repositories can be added by expanding the list under the `repositories` key.
This configuration must be placed in your `igor.yml` or `igor-local.yml`
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
git:
  repositories:
    - name: testRepo # name of the repository to monitor
      type: github # type of managed git provider. Currently only GitHub is supported
      project: testProject 
      url: https://github.com/testProject/testRepo.git
github:
  baseUrl: "https://api.github.com"
  accessToken: <TOKEN> # this token must have read access to reposiotires being monitored
  commitDisplayLength: 8
```

### Delivery Config
Once the Igor plugin is configured, a delivery config referencing the artifact can be specified.

Notes:
1. When Git artifact is used, Flux `GitRepository` resource are created per environment. e.g. if you have two environments
named `dev` and `prod`, Flux resources `git-github-testProject-testRepo-dev` and `git-github-testProject-testRepo-prod` are created.
2. Currently only Git source is supported. Flux's `HelmRepository` and `Bucket` kinds are not yet supported.
3. `tagVersionStrategy` supports all standard strategies except custom regex.
4. Individual resources created by Kustomize or Helm are not displayed in UI.

Kustomize example:
```yaml
name: demo1
application: fnord
serviceAccount: keeltest-service-account
artifacts:
- type: git
  reference: my-git-artifact
  tagVersionStrategy: semver-tag # other strategies are supported. Custom regex is not supported.
  repoName: testRepo
  project: testProject
  gitType: github
  secretRef: git-repo # This is passed to Flux's GitRepositorySpec.SecretRef field
  namespace: flux-system # optional. which namespace should be used to create Flux Source object. defaults to flux-system
  interval: 1m # optional. how often flux source controller should poll source. defaults to 1m
environments:
- name: test
  locations:
    account: deploy-experiments
    regions: []
  resources:
  - kind: k8s/kustomize@v1
    metadata:
      serviceAccount: keeltest-service-account
    spec:
      artifactSpec: 
        ref: my-git-artifact # Required
        namespace: test # override the artifacts[0].namespace value for this resource only
        interval: 10m # override the artifacts[0].interval value for this resource only
      metadata:
        application: fnord
      template:
        metadata:
          name: fnord-test
          namespace: flux-system
        spec: # Fields below are passed to Flux's KustomizationSpec
          interval: 1m
          path: "./kustomize"
          prune: true
          targetNamespace: test
```

Helm example:
```yaml
name: demo1
application: fnord
serviceAccount: keeltest-service-account
artifacts:
- type: git
  reference: my-git-artifact
  tagVersionStrategy: semver-tag
  repoName: testRepo
  project: testProject
  gitType: github
  secretRef: git-repo # This is passed to Flux's GitRepositorySpec.SecretRef field
environments:
- name: test
  locations:
    account: deploy-experiments
    regions: []
  resources:
  - kind: k8s/helm@v1
    spec:
      artifactSpec:
        ref: my-git-artifact # Required
      metadata:
        application: fnord
      template:
        metadata:
          name: crossplane
          namespace: flux-system
        spec: # Fields below are passed to Flux's HelmReleaseSpec
          releaseName: crossplane
          targetNamespace: crossplane-system
          chart:
            spec:
              chart: crossplane
          interval: 1m
          install:
            remediation:
              retries: 3
```

### Data flow

For an existing artifact definition:
1. The Igor plugin monitors given repositories for tag changes using Git provider's REST endpoints. Results are cached to Igor's configured Redis instance.
2. If the Igor plugin detects a new tag is created, it notifies Keel with detailed information about this tag. e.g. author, sha, date, etc
3. The keel plugin processes the event and store it as an artifact. 

For a new artifact definition:
1. The Keel plugin obtains the latest version of Git artifact through the REST endpoints exposed by the Igor plugin.
2. Obtained information is processed and stored as an artifact.

```
        Moonitor            Pull and
┌─────┐ with REST  ┌──────┐ Notify   ┌──────┐
│ Git ◄────────────► Igor ◄──────────► Keel │
└─────┘            └──────┘          └──────┘
```

</details>

### Supported Deployments & Verifications

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
<summary>Deploying HELM and Kustomzie charts</summary>

The plugin relies on [Flux2](https://github.com/fluxcd/flux2) for deployment of HELM and Kustomize resources.
This relieves the plugin from having to deal with the heavy lifting of managing changes to HELM charts
or Kustomization sources where that can be delegated to flux.

#### HELM 
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
to be updated to `k8s/helm@v1`, indicating deployment of a HELM chart using the plugin.

See the [Supported Artifact types](#supported-artifact-types) section for a complete example.

#### Kustomize
Similar to HELM deployments, you need to install Flux2 kustomize controller and source controller.

```bash
flux install \
    --namespace=flux-system \
    --network-policy=false \
    --components=source-controller,kustomize-controller
```

See the [Supported Artifact types](#supported-artifact-types) section for a complete example.

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

All secrets generated from clouddriver account information are tagged with the following two tags:

- `account.clouddriver.spinnaker.io/name=[CLOUDDRIVER-ACCOUNT-NAME]`
- `account.clouddriver.spinnaker.io/type=[CLOUDDRIVER-ACCOUNT-TYPE]` (e.g. `git`)

Generated secrets can be discovered like the following as an example:

```
kubectl get secrets -l account.clouddriver.spinnaker.io/type=git
```
</details>

<details>
<summary>Environment validation with verifyWith</summary>
This plugin supports environment validation using Kubernetes Job. An example configuration is provided below:

```yaml
name: test1
application: testapp
serviceAccount: keeltest-service-account
artifacts:
- name: nabuskey/test
  type: docker
  reference: my-docker-artifact
  tagVersionStrategy: increasing-tag
environments:
  - name: dev
    verifyWith: 
    - account: deploy-experiments # specifies which account to launch Kubernetes job.
      jobNamePrefix: my-prefix # Optional. If supplied, job names created by the plugin will start with this. 
      type: k8s/job@v1
      manifest: 
        metadata:
          # generateName: myname # Optional. If this field is specified, name will follow this field value. 
          name: not-used # the name field is ignored
          namespace: dev
        spec:
          template:
            spec:
              containers:
              - name: verify
                image: alpine
                command: ["ash",  "-c", "sleep 5; exit 0 "]
              restartPolicy: Never
          backoffLimit: 2
    locations:
      account: deploy-experiments
      regions: []
    resources: []
```

The Kubernetes job name is generated as `<PREFIX>-verify-<APPLICATION>-<ENVIRONMENT>-<ARTIFACT_VERSION>-<RANDOM_5_CHARS>"`
As an example, for the yaml specification above with a given artifact version `10`, a generated name could look something like the following: `my-prefix-verify-testapp-dev-10-uvxyz`

Jobs specified under `verifyWith` are executed after artifact promotion and when the environment is successfully matched to the desired end state.

If the `manifest.metadata.generateName`field is supplied, plugin will not generate name and the name is generated as specified in the filed.

Kubernetes jobs generated by `verifyWith` through this plugin will have three labels applied:
- `md.spinnaker.io/verify-environment` :  This contains the name of environment in which this `verifyWith` job is defined.
- `md.spinnaker.io/verify-artifact` : This contains the version of artifact which caused this `verifyWith` execution.
- `app.kubernetes.io/name` : Name of the application in which this `verifyWith` is defined.
</details>

## Build and Test

### Build and Install Locally
Run `./gradlew releaseBundle` and copy the created zip file, ` build/distributions/managed-delivery-k8s-plugin*.zip`, to
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

## Troubleshooting
### Kubernetes Resource Handling
#### Discovering Resources

- All kubernetes resources deployed via the plugin are labeled with the label `md.spinnaker.io/plugin: k8s`.
- Spinnaker's clouddriver labels deployed resources with the name of the application, e.g. for
  an application named `spinmd`, your created Kubernetes resources for this application are labeled with
  `app.kubernetes.io/name: spinmd`.
  
The combination should allow you to query for application resources created by the plugin.

Example commands:
```bash
# Get all resources created by this plugin
kubectl get all -l md.spinnaker.io/plugin -A
# Get all resources belongs to application named spinmd
kubectl get all -l md.spinnaker.io/plugin=k8s,app.kubernetes.io/name=spinmd -A
# Get jobs created by verifyWith for environment named dev
kubectl get job -l md.spinnaker.io/verify-environment=dev -A
```


#### Cleaning up resources
Currently, Keel does not remove resources even when a resource was deleted from its resource definitions.
As a result, Kubernetes resources are not removed automatically. If you wish to remove resources created by this plugin,
you need to search using labels described above. 


### Plugin loading
#### Verify plugins are loaded
If the plugin is loaded correctly, you will see log messages like the following:
```
Enabling spinnaker-official and spinnaker-community plugin repositories
Plugin 'aws.ManagedDeliveryK8sPlugin@unspecified' resolved
Start plugin 'aws.ManagedDeliveryK8sPlugin@unspecified'
starting ManagedDelivery k8s plugin.
```
If the plugin is not loading, verify the following:
1. Ensure the plugin root directory is readable and writable by the user that's running the service (keel, clouddriver, etc)
The plugin root is determined by the `spinnaker.extensibility.plugins-root-path` field in your service yml file. 
It defaults to the `plugins` directory in your current working directory. If using the Docker images, it defaults to
`/opt/<SERVICE_NAME/plugins`. e.g. for Keel, it defaults to `/opt/keel/plugins/`
   
2. Ensure the plugin is enabled. Ensure the `spinnaker.extensibility.plugins.aws.ManagedDeliveryK8sPlugin.enabled` is set to `true`
