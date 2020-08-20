# Managed Delivery K8s Plugin

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
