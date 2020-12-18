# Managed Delivery K8s Plugin

## Usage
Add the following to `keel.yml` in the necessary [profile](https://spinnaker.io/reference/halyard/custom/#custom-profiles) to load plugin.
```yaml
spinnaker:
    extensibility:
      plugins:
        aws.ManagedDeliveryK8sPlugin:
          id: aws.ManagedDeliveryK8sPlugin
          enabled: true
          version: <<plugin release version>>
      repositories:
        awsManagedDelvieryK8sPluginRepo:
          id: awsManagedDelvieryK8sPluginRepo
          url: https://raw.githubusercontent.com/nimakaviani/managed-delivery-k8s-plugin/master/plugins.json
```



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
