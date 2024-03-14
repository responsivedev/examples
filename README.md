# Responsive Examples

This repository contains an example Kafka Streams application that is powered
by the Responsive Platform for Kafka Streams. There are two modules: the
`streams-app` module, which contains the application logic (described below),
and the `pulumi` module, which contains the infrastructure-as-code definition
required for deploying the streams application into an AWS EKS cluster with
the Responsive operator.

This repo also allows you to get up and running using a local Kubernetes In Docker
(kind) cluster.

## Creating your cluster.
### KinD

The bootstrapping will use secrets that are expected to be located inside the `secrets`
directory within this repository's working directory. The following secrets are required:

```
responsive-metrics-creds.properties (Responsive Metrics Key)
```

The streams app has to be built locally:

```
$ ./gradlew :streams-app:jibDockerBuild
```

You are now ready to create and bootstrap the KinD cluster:
```
$ bash ./kind/bootstrap.sh
```

### Pulumi
If you are using Pulumi to create your cluster, you can run `pulumi up` from within the 
`pulumi` directory.

## Building The Streams App

There are a few phases for building this project - when making changes to the
`streams-app` module, use `./gradlew build` to ensure that it can build locally
and then use `./gradlew :streams-app:jib` to build the docker image for the
streams application (note that this requires AWS ECR credentials to push to
the public ECR repository).

To deploy the streams app into an EKS cluster, `cd` into the `pulumi` dir. First,
you need to set the secrets for the Kafka Cluster credentials and ScyllaDB cluster 
credentials. 

**NOTE:** at the moment, the kafka cluster and scyllaDB cluster/user are hard-coded.
We should update this to allow public usage of this repository.

```bash
pulumi config set --secret kafkaSaslJaasConfig "org.apache.kafka.common.security.plain.PlainLoginModule required username='<USERNAME>' password='<PASSWORD>';" 
pulumi config set --secret responsiveClientSecret <responsive_secret>
```

Then run `pulumi up` (ensure you have `pulumi` installed).

## Running the Operator
Follow the instructions on https://cloud.responsive.dev to run the operator in your cluster.